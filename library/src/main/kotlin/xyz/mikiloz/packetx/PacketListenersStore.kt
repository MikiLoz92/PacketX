package xyz.mikiloz.packetx

interface PacketListenersStore<P> {

    /**
     * Add a [PacketListener] for a specific [type][clazz] of packet.
     */
    fun <T : P> addOnPacketReceivedListener(clazz: Class<P>, listener: PacketListener<T>): PacketListener<T>

    /**
     * Add a [listener function][listener] for a specific [type][clazz] of packet. The function will be
     * converted to a [PacketListener] interface for Java compatibility purposes.
     *
     * @return The converted [PacketListener] instance, so you can remove the listener later on by using this reference.
     */
    fun <T : P> addOnPacketReceivedListener(clazz: Class<P>, listener: (packet: T) -> Unit): PacketListener<T>

    /**
     * Remove a [PacketListener].
     *
     * @return Whether the listener was found and was successfully removed.
     */
    fun <T : P> removeOnPacketReceivedListener(clazz: Class<P>, listener: PacketListener<T>): Boolean

    /**
     * Remove a [listener][listener] using a function reference.
     *
     * @return Whether the listener was found and was successfully removed.
     */
    fun <T : P> removeOnPacketReceivedListener(clazz: Class<P>, listener: (packet: T) -> Unit): Boolean

    /**
     * Clear all the listeners on a given packet [type][P].
     */
    fun clearOnPacketReceivedListeners(clazz: Class<P>)

    fun <T : P> onPacketReceived(clazz: Class<P>, packet: T)

    /**
     * Listens for an incoming packet of a given [type][T].
     */
    interface PacketListener<in T> {
        fun onPacketReceived(packet: T)
    }

}