/*
 * PolyBootstrap - A Discord bot for the Polyhedral Development discord server
 * Copyright (c) 2021-2021 solonovamax <solonovamax@12oclockpoint.com>
 *
 * The file Launcher.kt is part of PolyBootstrap
 * Last modified on 14-10-2021 11:14 p.m.
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * POLYBOOTSTRAP IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ca.solostudios.polybot.bootstrap

import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.delimiter
import kotlinx.cli.multiple
import kotlinx.cli.optional
import kotlinx.cli.vararg
import kotlinx.coroutines.future.await
import org.slf4j.kotlin.debug
import org.slf4j.kotlin.error
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info
import org.slf4j.kotlin.warn
import kotlin.system.exitProcess

class Launcher(
        private val updateTask: UpdateTask,
        args: Array<String>,
        parser: ArgParser = ArgParser("polybot.bootstrap"),
              ) {
    private val logger by getLogger()
    
    private val version by parser.option(
            ArgType.Boolean,
            shortName = "v",
            fullName = "version",
            description = "Prints the current version of this application",
                                        ).default(false)
    private val jvmArgs by parser.option(
            ArgType.String,
            shortName = "j",
            fullName = "jvm-args",
            description = """
                Sets the JVM arguments that are provided to the bot process.
                Use ; to delimit multiple JVM args, or specify multiple times.
            """.trimIndent(),
                                        ).delimiter(";").multiple()
    private val heapSize by parser.option(
            ArgType.String,
            shortName = "x",
            fullName = "heap",
            description = """
                Sets the JVM maximum heap size for the bot process.
                See the java documentation for -Xmx for more info.
            """.trimIndent(),
                                         )
    private val initialHeapSize by parser.option(
            ArgType.String,
            shortName = "s",
            fullName = "initial-heap",
            description = """
                Sets the JVM initial heap size for the bot process.
                See the java documentation for -Xms for more info.
            """.trimIndent(),
                                                )
    private val jenkinsBaseUrl by parser.option(
            ArgType.String,
            fullName = "jenkins-base",
            description = """
                Sets the Jenkins base URL used to resolve the project updater.
                Ex. https://ci.example.com
            """.trimIndent(),
                                               ).default("https://ci.solo-studios.ca")
    
    private val jenkinsRelativeProjectUrl by parser.option(
            ArgType.String,
            fullName = "jenkins-project",
            description = """
                Sets the project url for Jenkins, relative to the base url.
                Ex. job/solo-studios/job/PolyBot
            """.trimIndent()
                                                          ).default("job/solo-studios/job/PolyBot")
    
    private val jarLocation by parser.option(
            ArgType.String,
            shortName = "f",
            description = """
                Sets the location at which the jar file is stored for the bot.
                If the jar file already exists, it will load that jar. If not, it will download the jar from the configured CI server.
            """.trimIndent()
                                            ).default("PolyBot.jar")
    
    private val arguments by parser.argument(
            ArgType.String,
            fullName = "arguments",
            description = """
                The list of arguments to be passed to the bot.
                See the documentation for the bot.
            """.trimIndent(),
                                            ).optional().vararg()
    
    init {
        parser.parse(args)
    }
    
    private val jarFile = File(jarLocation)
    private var recentBoots = 0
    private var lastStartAttemptTime = Instant.MIN
    private var hasOldJar = false
    private val oldJarFile = File("old.${jarFile.path}")
    
    suspend fun run() {
        if (!jarFile.exists()) {
            logger.info { "Jar file does not exist. Downloading from CI..." }
            updateTask.update(jarFile)
        }
        
        if (oldJarFile.exists()) {
            oldJarFile.delete()
        }
        
        logger.info { "Starting bot process..." }
        
        while (true) {
            val now = Instant.now()
            val duration = Duration.between(lastStartAttemptTime, now)
            if (duration > trackedBootExpiry) {
                recentBoots = 0
                logger.info { "Reset boots" }
            }
            
            
            if (recentBoots >= maxBootsBeforeShutdown) {
                if (hasOldJar) {
                    jarFile.delete()
                    oldJarFile.renameTo(jarFile)
                    logger.error { "Failed to start $maxBootsBeforeShutdown times within 30 seconds of each boot. This is probably due to an error." }
                    logger.warn { "Reverted jar file to older version, as the new version seems to cause updates. !! Warning !! This is only a temporary fix." }
                } else {
                    logger.error { "Failed to start $maxBootsBeforeShutdown times within 30 seconds of each boot. This is probably due to an error. Exiting." }
                    exitProcess(ExitCodes.EXIT_CODE_ERROR)
                }
            }
            lastStartAttemptTime = now
            
            val process = startProcess().let {
                recentBoots++
                it.onExit()
            }.await()
            
            val exitCode = process.exitValue()
            logger.debug { "Bot process exited with code $exitCode" }
            
            when (exitCode) {
                ExitCodes.EXIT_CODE_NORMAL   -> {
                    logger.info { "Bot exited cleanly." }
                    exitProcess(0)
                }
                
                ExitCodes.EXIT_CODE_SHUTDOWN -> {
                    logger.info { "Bot exited successfully, requesting a shutdown. Shutting down bootstrap process." }
                    exitProcess(0)
                }
                
                ExitCodes.EXIT_CODE_ERROR    -> {
                    logger.error { "Bot exited with an error. Please check the bot logs for more information." }
                }
                
                ExitCodes.EXIT_CODE_RESTART  -> {
                    logger.info { "Bot exited successfully, requesting a restart." }
                }
                
                ExitCodes.EXIT_CODE_UPDATE   -> {
                    logger.info { "Bot exited successfully, requesting an update." }
    
                    logger.info { "Updating bot jar." }
                    try {
                        if (oldJarFile.exists())
                            oldJarFile.delete()
        
                        jarFile.renameTo(oldJarFile)
                        hasOldJar = true
        
                        updateTask.update(jarFile)
                    } catch (e: Exception) {
                        logger.error(e) { "Exception while updating jar!" }
                        exitProcess(1)
                    }
                    logger.info { "Bot jar updated successfully." }
                }
                
                else                         -> {
                    logger.info { "Bot exited with unknown error code, $exitCode." }
                }
            }
            
            logger.info { "Restarting bot process..." }
        }
    }
    
    private fun startProcess(): Process {
        val processBuilder = ProcessBuilder()
                .inheritIO()
        
        processBuilder.environment()
        
        val args = mutableListOf("java")
        
        args += "-Dfile.encoding=UTF-8"
        
        if (heapSize != null)
            args += "-Xmx$heapSize"
        if (initialHeapSize != null)
            args += "-Xms$initialHeapSize"
        
        args += jvmArgs
    
        args += listOf("-jar", jarLocation)
        
        args += arguments
        
        logger.debug { args.joinToString(separator = ", ", prefix = "Launching process [", postfix = "]") }
        
        return processBuilder.command(args).start()
    }
    
    companion object {
        private val trackedBootExpiry = Duration.ofSeconds(30)
        private const val maxBootsBeforeShutdown = 3
    }
}