package xyz.mikiloz.packetx

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.SingleEmitter
import xyz.mikiloz.packetx.PacketListenersStore.PacketListener
import java.util.*
import kotlin.concurrent.schedule

/**
 * This class provides a means to create a packet-based controller, which you can use to:
 * * Subscribe packet listeners and get notified when a certain packet arrives.
 * * Perform simple TX/RX operations on the medium expecting an asynchronous response from these operations.
 * * Perform more complex TX/RX operations with multiple TX packets and/or multiple RX packets, watching the response
 * as an [Observable].
 *
 * @constructor Create a packet-based controller from your own [PacketCourier] implementation.
 */
class PacketBasedController<P : Any>(private val courier: PacketCourier<P>) {

    init {
        courier.packetListenersStore = PacketListenersStoreImpl(courier)
    }

    /**
     * Uses the [PacketCourier] implementation you provided in this class' constructor to write a packet into the
     * medium.
     */
    fun blockingWritePacket(packet: P) = courier.transmitPacket(packet)

    /**
     * Uses the [PacketCourier] implementation you provided in this class' constructor to write a packet into the
     * medium.
     *
     * @return A [Completable] of the action.
     */
    fun writePacket(packet: P) = Completable.create { courier.transmitPacket(packet); it.onComplete() }

    /**
     * This method constructs a temporary listener that listens to your packet [type][clazz] of interest and lives just
     * as long as the packet takes to arrive, or a given [timeout], whatever happens before.
     *
     * @param timeout The timeout to use with the listener, in milliseconds. When reaching this value, the controller
     * will give up and delete the listener. Use a value of 0 to have it await indefinitely (Be careful though! This
     * will prevent the listener from being deleted until the packet is received, which can cause issues if you're
     * constantly adding listeners).
     */
    fun <T : P> expectPacket(clazz: Class<P>, timeout: Long = 2000): Single<T> {

        lateinit var listener: (packet: T) -> Unit

        val expectationSingle = Single.create<T> { emitter: SingleEmitter<T> ->
            var success = false
            val timer = Timer()

            val dispose: () -> Unit = {
                timer.cancel()
            }

            listener = { packet: T ->
                emitter.onSuccess(packet)
                success = true
                dispose()
            }
            courier.packetListenersStore.addOnPacketReceivedListener(clazz, listener)

            if (timeout != 0L) timer.schedule(timeout) {
                if (!success) emitter.onError(RequestTimeoutException())
                dispose()
            }
        }

        return expectationSingle.doFinally { courier.packetListenersStore.removeOnPacketReceivedListener(clazz, listener) }
    }

    /**
     * This method constructs a temporary listener that listens to your packet [type][T] of interest and lives just
     * as long as the packet takes to arrive, or a given [timeout], whatever happens before.
     *
     * @param timeout The timeout to use with the listener, in milliseconds. When reaching this value, the controller
     * will give up and delete the listener. Use a value of 0 to have it await indefinitely (Be careful though! This
     * will prevent the listener from being deleted until the packet is received, which can cause issues if you're
     * constantly adding listeners).
     */
    inline fun <reified T : P> expectPacket(timeout: Long = 2000): Single<T> =
        expectPacket(T::class.java as Class<P>, timeout)

    /**
     * This method performs a [request] and constructs a temporary listener (that expects packets of the given
     * [type][clazz]) that lives just as long as the request isn't yet over, defined by your [isOver] discriminator
     * function.
     *
     * @param timeout The timeout of the request, in milliseconds. After this, the returned observable will yield a
     * [RequestTimeoutException] error.
     * @param listeningDelay An optional delay to start listening for packets.
     * @param isOver The discriminator function. This function receives the packets as they arrive, and decides whether
     * or not this request has ended (and thus the temporary listener created is deleted), judging from the packets
     * themselves.
     */
    fun <T : P> makeRequest(clazz: Class<P>,
                            request: P,
                            timeout: Long,
                            listeningDelay: Long = 0,
                            isOver: (packet: T) -> Boolean = { true }) : Observable<T> {

        lateinit var listener: (packet: T) -> Unit

        val responseObservable = Observable.create<T> { emitter ->
            var complete = false
            val timer = Timer()

            val dispose: () -> Unit = {
                timer.cancel()
            }

            listener = { packet: T ->
                emitter.onNext(packet)
                complete = isOver(packet)
                if (complete) {
                    emitter.onComplete()
                    dispose()
                }
            }

            timer.schedule(listeningDelay) { addOnPacketReceivedListener(clazz, listener) }
            timer.schedule(listeningDelay + timeout) {
                if (!complete) emitter.onError(RequestTimeoutException(request))
                dispose()
            }
        }

        return writePacket(request).andThen(responseObservable).doFinally {
            removeOnPacketReceivedListener(clazz, listener)
        }

    }

    /**
     * @see [makeRequest]
     */
    inline fun <reified T : P> makeRequest(request: P,
                                           timeout: Long,
                                           listeningDelay: Long = 0,
                                           noinline isOver: (packet: T) -> Boolean = { true }) : Observable<T> =
            makeRequest(T::class.java as Class<P>, request, timeout, listeningDelay, isOver)

    /**
     * This utility method performs a call to [makeRequest] with the assumption that your request will only yield one
     * packet of said [type][clazz] as a response.
     *
     * @param timeout The timeout of the request, in milliseconds. After this, the returned observable will yield a
     * [RequestTimeoutException] error.
     * @param listeningDelay An optional delay to start listening for packets.
     */
    fun <T : P> makeSinglePacketRequest(clazz: Class<P>,
                                        request: P,
                                        timeout: Long,
                                        listeningDelay: Long = 0) : Single<T> =
            makeRequest<T>(clazz, request, timeout, listeningDelay, {true}).singleOrError()

    /**
     * @see [makeSinglePacketRequest]
     */
    inline fun <reified T : P> makeSinglePacketRequest(request: P,
                                                       timeout: Long,
                                                       listeningDelay: Long = 0) : Single<T> =
            makeSinglePacketRequest(T::class.java as Class<P>, request, timeout, listeningDelay)

    /**
     * This utility method performs a call to [makeRequest] with the assumption that your request will only yield one
     * packet of said [type][clazz] as a response, and you're not interested in the packet's contents.
     *
     * @param timeout The timeout of the request, in milliseconds. After this, the returned observable will yield a
     * [RequestTimeoutException] error.
     * @param listeningDelay An optional delay to start listening for packets.
     */
    fun <T : P> makeCompletableRequest(clazz: Class<P>,
                            request: P,
                            timeout: Long,
                            listeningDelay: Long = 0) : Completable =
            makeRequest<T>(clazz, request, timeout, listeningDelay, {true}).singleOrError().toCompletable()

    /**
     * @see [makeCompletableRequest]
     */
    inline fun <reified T : P> makeCompletableRequest(request: P,
                                           timeout: Long,
                                           listeningDelay: Long = 0) : Completable =
            makeCompletableRequest<T>(T::class.java as Class<P>, request, timeout, listeningDelay)


    //region Listener methods

    /**
     * Add a [PacketListener] for a specific [type][clazz] of packet.
     */
    fun <T : P> addOnPacketReceivedListener(clazz: Class<P>, listener: PacketListenersStore.PacketListener<T>) =
            courier.packetListenersStore.addOnPacketReceivedListener(clazz, listener)

    /**
     * Add a [PacketListener] for a specific [type][T] of packet.
     */
    inline fun <reified T : P> addOnPacketReceivedListener(listener: PacketListenersStore.PacketListener<T>) =
            addOnPacketReceivedListener<T>(T::class.java as Class<P>, listener)

    /**
     * Add a [listener function][listener] for a specific [type][clazz] of packet. The function will be
     * converted to a [PacketListener] interface for Java compatibility purposes.
     *
     * @return The converted [PacketListener] instance, so you can remove the listener later on by using this reference.
     */
    fun <T : P> addOnPacketReceivedListener(clazz: Class<P>, listener: (packet: T) -> Unit) =
            courier.packetListenersStore.addOnPacketReceivedListener(clazz, listener)

    /**
     * Add a [listener function][listener] for a specific [type][clazz] of packet. The function will be
     * converted to a [PacketListener] interface for Java compatibility purposes.
     *
     * @return The converted [PacketListener] instance, so you can remove the listener later on by using this reference.
     */
    inline fun <reified T : P> addOnPacketReceivedListener(noinline listener: (packet: T) -> Unit) =
            addOnPacketReceivedListener(T::class.java as Class<P>, listener)

    /**
     * Remove a [PacketListener].
     *
     * @return Whether the listener was found and was successfully removed.
     */
    fun <T : P> removeOnPacketReceivedListener(clazz: Class<P>, listener: PacketListenersStore.PacketListener<T>) =
            courier.packetListenersStore.removeOnPacketReceivedListener(clazz, listener)

    /**
     * Remove a [PacketListener].
     *
     * @return Whether the listener was found and was successfully removed.
     */
    inline fun <reified T : P> removeOnPacketReceivedListener(listener: PacketListenersStore.PacketListener<T>) =
            removeOnPacketReceivedListener(T::class.java as Class<P>, listener)

    /**
     * Remove a [listener][listener] using a function reference.
     *
     * @return Whether the listener was found and was successfully removed.
     */
    fun <T : P> removeOnPacketReceivedListener(clazz: Class<P>, listener: (packet: T) -> Unit) =
            courier.packetListenersStore.removeOnPacketReceivedListener(clazz, listener)

    /**
     * Remove a [PacketListener].
     *
     * @return Whether the listener was found and was successfully removed.
     */
    inline fun <reified T : P> removeOnPacketReceivedListener(noinline listener: (packet: T) -> Unit) =
            removeOnPacketReceivedListener(T::class.java as Class<P>, listener)

    /**
     * Clear all the listeners on a given packet [type][P].
     */
    fun clearOnPacketReceivedListeners(clazz: Class<P>) =
            courier.packetListenersStore.clearOnPacketReceivedListeners(clazz)

    /**
     * Clear all the listeners on a given packet [type][T].
     */
    inline fun <reified T : P> clearOnPacketReceivedListeners() = clearOnPacketReceivedListeners(T::class.java as Class<P>)

    //endregion

    class RequestTimeoutException(request: Any = Unit) : Exception("Timeout while performing request \"$request\"")

}