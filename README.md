# PacketX

PacketX is a set of ReactiveX extensions for dealing with packet-based communications systems. Any medium you have to communicate with which its data can be separated into fragments (more specifically packets), and can be further divided into different kinds of packets is susceptible of benefiting from using this library.

With PacketX you can:
 * Set up listeners for your incoming packets
 * Make requests and obtain the responses as Observables, Singles or Completables

## Usage

To include this project in your codebase just put JitPack's repository on your `build.gradle` file:
```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
and then add the dependency:
```gradle
dependencies {
    implementation 'com.github.MikiLoz92:PacketX:0.1.0'
}
```
Using the library is rather easy:

### 1. Implement `PacketCourier<T>`

First thing to do is implement the `PacketCourier<T>` interface, type `T` being the base class of the type of packet you'll be expecting. For example, if you're working on a CAN line consider creating a `CanPacket` class to act as the parent of all other individual types of CAN packets.

The `PacketCourier` interface's job is to provide a means of interacting with the physical line you're dealing with. You'll have to implement a `transmitPacket` method that the system will end up using when you perform a request using the PacketX controller.

On top of this, you will have to implement a `PacketListenersStore<T>` property on your `PacketCourier<T>`. Let's see how all of this is done:

```kotlin
class CanController : PacketCourier<CanPacket> {

    // When initializing the controller later on this property will be initialized for you, so you can leave it as a lateinit for now.
    override lateinit var packetListenersStore
    
    override fun transmitPacket(packet: CanPacket) {
        // Do your magic here: transmit this packet into the CAN line.
    }
}
```

### 2. Initialize a `PacketBasedController<T>`

The second (and last!) step is to initialize a `PacketBasedController<T>`, which is rather easy to do:
```kotlin
var canController = ... // Create a CanController (which implements PacketCourier) instance
var packetBasedCanController = PacketBasedController<CanPacket>(canController) // Pass a PacketCourier instance
```

Now you just have to use the newly created `packetBasedController`:
```kotlin
var request = CanPacket.PingPacket()
packetBasedController.makeSinglePacketRequest<CanPacket.PongPacket>(request, timeout = 2000)
        .subscribe { packet: CanPacket.PongPacket ->
            println("PONG packet received: $packet")
        }
```
