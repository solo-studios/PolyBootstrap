/*
 * PolyBootstrap - A Discord bot for the Polyhedral Development discord server
 * Copyright (c) 2021-2021 solonovamax <solonovamax@12oclockpoint.com>
 *
 * The file Launcher.kt is part of PolyBootstrap
 * Last modified on 11-10-2021 11:34 p.m.
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
import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess

class Launcher(args: Array<String>, parser: ArgParser = ArgParser("polybot.bootstrap")) {
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
            """.trimIndent()
                                                )
    private val arguments by parser.argument(
            ArgType.String,
            fullName = "arguments",
            description = """
                The list of arguments to be passed to the bot.
            """.trimIndent(),
                                            ).optional().vararg()
    
    private var recentBoots = 0
    private var lastStartAttemptTime = Instant.MIN
    
    init {
        parser.parse(args)
    }
    
    suspend fun run() {
        logger.info { "Starting bot process..." }
        
        while (true) {
            val now = Instant.now()
            val duration = Duration.between(lastStartAttemptTime, now)
            if (duration > trackedBootExpiry) {
                recentBoots = 0
                logger.info { "Reset boots" }
            }
            if (recentBoots >= maxBootsBeforeShutdown) {
                logger.error { "Failed to start $maxBootsBeforeShutdown times within 30 seconds of each boot. This is probably due to an error. Exiting." }
                exitProcess(ExitCodes.EXIT_CODE_ERROR)
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
                    logger.info { "Bot exited successfully." }
                }
                
                ExitCodes.EXIT_CODE_SHUTDOWN -> {
                    logger.info { "Bot exited successfully, requesting a shutdown. Shutting down bootstrap process." }
                    exitProcess(0)
                }
                
                ExitCodes.EXIT_CODE_ERROR    -> {
                    logger.error { "Bot exited with an error." }
                }
                
                ExitCodes.EXIT_CODE_RESTART  -> {
                    logger.info { "Bot exited successfully, requesting a restart." }
                }
                
                ExitCodes.EXIT_CODE_UPDATE   -> {
                    logger.info { "Bot exited successfully, requesting an update. Updating bot jar." }
                    // TODO: 2021-10-11 Update jar
                }
                
                else                         -> {
                    logger.info { "Bot exited with unknown error code." }
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
        
        args += listOf("-jar", "PolyBot.jar")
        
        args += arguments
        
        logger.debug { args.joinToString(separator = ", ", prefix = "Launching process [", postfix = "]") }
        
        return processBuilder.command(args).start()
    }
    
    companion object {
        private val trackedBootExpiry = Duration.ofSeconds(30)
        private const val maxBootsBeforeShutdown = 3
    }
}