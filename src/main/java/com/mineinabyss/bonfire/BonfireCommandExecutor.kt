package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.extensions.bonfireData
import com.mineinabyss.bonfire.extensions.removeBonfireSpawnLocation
import com.mineinabyss.bonfire.extensions.setRespawnLocation
import com.mineinabyss.idofront.commands.CommandHolder
import com.mineinabyss.idofront.commands.arguments.intArg
import com.mineinabyss.idofront.commands.arguments.stringArg
import com.mineinabyss.idofront.commands.execution.ExperimentalCommandDSL
import com.mineinabyss.idofront.commands.execution.IdofrontCommandExecutor
import com.mineinabyss.idofront.commands.execution.stopCommand
import com.mineinabyss.idofront.commands.extensions.actions.playerAction
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.info
import com.mineinabyss.idofront.messaging.success
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Campfire
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

@ExperimentalCommandDSL
object BonfireCommandExecutor : IdofrontCommandExecutor() {
    override val commands: CommandHolder = commands(bonfirePlugin) {
        ("bonfire" / "bf")(desc = "Commands for Bonfire") {
            "respawn"(desc = "Commands to manipulate the Bonfire respawn of players") {
                val targetPlayerStr by stringArg { name = "player" }

                "get"(desc = "Get the player respawn from the database") {
                    playerAction {
                        val offlineTargets = Bukkit.getOfflinePlayers()
                            .filter { it.name == targetPlayerStr }
                            .distinctBy { it.uniqueId }
                        val offlineTargetsUUIDs = offlineTargets.map { it.uniqueId }

                        if (offlineTargets.isEmpty()) {
                            command.stopCommand("No player found with that name")
                        }

                        if (offlineTargets.size > 1) {
                            sender.info("Multiple players found with that name, checking respawn for all.")
                        }

                        transaction {
                            val dbPlayers = Players
                                .leftJoin(Bonfire, { bonfireUUID }, { entityUUID })
                                .select { Players.playerUUID inList offlineTargetsUUIDs }

                            offlineTargets.forEach { player ->
                                val dbPlayer = dbPlayers.firstOrNull { it[Players.playerUUID] == player.uniqueId }

                                if (dbPlayer == null) {
                                    sender.info("Player ${player.name} does not have a bonfire respawn set.")
                                } else {
                                    if (!dbPlayer.hasValue(Bonfire.entityUUID)) {
                                        sender.error("Bonfire for player ${player.name} not found in the database. This is bad and should not happen!")
                                    } else {
                                        sender.info("Bonfire for player ${player.name} is at ${dbPlayer[Bonfire.location]}.")
                                    }
                                }
                            }
                        }
                    }
                }
                "set"(desc = "Set the player bonfire respawn point in the database. Ignores bonfire max player limit. Need to be in the world of the bonfire!") {
                    val bonfireLocX by intArg { name = "X" }
                    val bonfireLocY by intArg { name = "Y" }
                    val bonfireLocZ by intArg { name = "Z" }

                    playerAction {
                        val offlineTargets = Bukkit.getOfflinePlayers()
                            .filter { it.name == targetPlayerStr }
                            .distinctBy { it.uniqueId }

                        if (offlineTargets.isEmpty()) {
                            command.stopCommand("No player found with that name")
                        }

                        if (offlineTargets.size > 1) {
                            command.stopCommand("Multiple players found with that name, not setting respawn.")
                        }

                        val bonfireUUID = (Location(
                            player.world,
                            bonfireLocX.toDouble(),
                            bonfireLocY.toDouble(),
                            bonfireLocZ.toDouble()
                        ).block.state as? Campfire)?.bonfireData()?.uuid

                        if (bonfireUUID == null) {
                            command.stopCommand("No bonfire found at this location.")
                        } else {
                            val targetPlayer = offlineTargets.first()
                            targetPlayer.setRespawnLocation(bonfireUUID)
                            sender.info("Respawn set for player ${targetPlayer.name}")
                        }
                    }
                }
                "remove"(desc = "Remove the player bonfire respawn point.") {
                    playerAction {
                        val offlineTargets = Bukkit.getOfflinePlayers()
                            .filter { it.name == targetPlayerStr }
                            .distinctBy { it.uniqueId }

                        if (offlineTargets.isEmpty()) {
                            command.stopCommand("No player found with that name.")
                        }

                        if (offlineTargets.size > 1) {
                            command.stopCommand("Multiple players found with that name, not removing respawn.")
                        }

                        val targetPlayer = offlineTargets.first()

                        transaction {
                            val bonfireUUID = Players
                                .select { Players.playerUUID eq targetPlayer.uniqueId }
                                .firstOrNull()?.get(Players.bonfireUUID)

                            if (bonfireUUID == null) {
                                command.stopCommand("Player does not have a respawn set.")
                            } else {
                                if (targetPlayer.removeBonfireSpawnLocation(bonfireUUID)) {
                                    sender.info("Respawn removed from player ${targetPlayer.name}.")
                                } else {
                                    sender.error("Failed to remove respawn from player ${targetPlayer.name}.")
                                }
                            }
                        }
                    }
                }
            }
            "bonfire"(desc = "Commands to get bonfire info") {
                "check"(desc = "Check if a bonfire at location is stored in the database") {
                    val bonfireLocX by intArg { name = "X" }
                    val bonfireLocY by intArg { name = "Y" }
                    val bonfireLocZ by intArg { name = "Z" }
                    playerAction {
                        val bonfireUUID = (Location(
                            player.world,
                            bonfireLocX.toDouble(),
                            bonfireLocY.toDouble(),
                            bonfireLocZ.toDouble()
                        ).block.state as? Campfire)?.bonfireData()?.uuid

                        if (bonfireUUID == null) {
                            command.stopCommand("No bonfire found at this location.")
                        } else {
                            transaction {
                                if (Bonfire.select { Bonfire.entityUUID eq bonfireUUID }.any()) {
                                    sender.success("Bonfire is registered in the database.")
                                } else {
                                    sender.error("Bonfire is not registered in the database.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}