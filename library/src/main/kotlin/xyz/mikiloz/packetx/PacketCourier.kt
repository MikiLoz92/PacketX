package xyz.mikiloz.packetx


/**
 * Override this interface to provide a use case-specific implementation of the packet delivery system that you're
 * using.
 *
 * @param P The type of packet you'll be dealing with.
 */
interface PacketCourier<P : Any> {

    /**
     * You don't have to initialize this property, just leave it as a **`lateinit`** property or provide a simple
     * implementation means if you're in Java (`null` it by default!).
     */
    var packetListenersStore: PacketListenersStore<P>

    /**
     * Transmit a given [P] packet into the physical medium. Override this function to provide PacketX with the
     * necessary logic to transmit a packet.
     *
     * This method **should be blocking**, and return only when the packet has just been effectively written into the
     * medium. So, for example, if your controller is based on a FIFO queue, where you add messages into it and they
     * get transmitted only once a second, this method should not return until the messages are effectively sent and
     * deleted from the queue. *How to pull this off* is totally up to you :D.
     */
    fun transmitPacket(packet: P)


    /**
     * Receive a [P] packet from the physical medium. You **should NOT** override this method!
     */
    @JvmDefault
    fun receivePacket(clazz: Class<P>, packet: P) {
        packetListenersStore.onPacketReceived(clazz, packet)
    }

}