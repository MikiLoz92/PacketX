package xyz.mikiloz.packetx

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * Default, thread-safe implementation of the [PacketListenersStore] interface.
 */
class PacketListenersStoreImpl<P : Any>(private val courier: PacketCourier<P>) : PacketListenersStore<P> {

    init {
        courier.packetListenersStore = this
    }


    private val packetListeners: ConcurrentHashMap<KClass<P>, CopyOnWriteArrayList<PacketListenersStore.PacketListener<P>>> = ConcurrentHashMap()
    private val listenerReferences: ConcurrentHashMap<Any, PacketListenersStore.PacketListener<P>> = ConcurrentHashMap()


    @Suppress("UNCHECKED_CAST")
    @Synchronized
    override fun <T : P> addOnPacketReceivedListener(clazz: Class<P>, listener: PacketListenersStore.PacketListener<T>): PacketListenersStore.PacketListener<T> {
        if (!packetListeners.containsKey(clazz.kotlin)) packetListeners[clazz.kotlin] = CopyOnWriteArrayList()
        packetListeners[clazz.kotlin]?.add(listener as PacketListenersStore.PacketListener<P>)
        return listener
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    override fun <T : P> addOnPacketReceivedListener(clazz: Class<P>, listener: (packet: T) -> Unit): PacketListenersStore.PacketListener<T> {
        val l = object : PacketListenersStore.PacketListener<T> {
            override fun onPacketReceived(packet: T) {
                listener(packet)
            }
        }
        addOnPacketReceivedListener(clazz, l)
        listenerReferences[listener] = l as PacketListenersStore.PacketListener<P>
        return l
    }

    @Synchronized
    override fun <T : P> removeOnPacketReceivedListener(clazz: Class<P>, listener: PacketListenersStore.PacketListener<T>): Boolean {
        return packetListeners[clazz.kotlin]?.remove(listener) ?: false
    }

    @Synchronized
    override fun <T : P> removeOnPacketReceivedListener(clazz: Class<P>, listener: (packet: T) -> Unit): Boolean {
        for (r in listenerReferences.keys) {
            if (r == listener) {
                val result = packetListeners[clazz.kotlin]?.remove(listenerReferences[listener]) ?: false
                listenerReferences.remove(listener)
                return result
            }
        }
        return false
    }

    @Synchronized
    override fun clearOnPacketReceivedListeners(clazz: Class<P>) {
        packetListeners[clazz.kotlin]?.clear()
    }


    override fun <T : P> onPacketReceived(clazz: Class<P>, packet: T) {
        val listeners = packetListeners[clazz.kotlin]?.listIterator()
        if (listeners != null) {
            for (l in listeners) {
                l.onPacketReceived(packet)
            }
        }
    }

}