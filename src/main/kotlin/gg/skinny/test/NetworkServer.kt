package gg.skinny.test

// Which server on the network this plugin is running on, and its details,
// set per container in network/docker-compose.yml.
// Without them (./gradlew runServer) it behaves like the old single lifesteal server.
enum class ServerType { LOBBY, LIFESTEAL, KITPVP, MINIGAME }

object NetworkServer {
    val type: ServerType = System.getenv("SKINNY_SERVER")
        ?.let { name -> ServerType.values().firstOrNull { it.name.equals(name, ignoreCase = true) } }
        ?: ServerType.LIFESTEAL

    // Lets several copies of one server type be told apart, e.g. Lobby #1 and Lobby #2.
    val number: Int = System.getenv("SKINNY_SERVER_NUMBER")?.toIntOrNull() ?: 1

    // The address players type to join, shown at the bottom of the lobby sidebar.
    val ip: String = System.getenv("SKINNY_IP") ?: "play.skinnymc.net"
}
