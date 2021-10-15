/*
 * PolyBootstrap - A Discord bot for the Polyhedral Development discord server
 * Copyright (c) 2021-2021 solonovamax <solonovamax@12oclockpoint.com>
 *
 * The file JenkinsUpdateTask.kt is part of PolyBootstrap
 * Last modified on 14-10-2021 11:24 p.m.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.awaitUnit
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.jackson.jacksonDeserializerOf
import java.io.File
import java.time.Instant
import org.slf4j.kotlin.debug
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info
import kotlin.math.ln
import kotlin.math.pow

class JenkinsUpdateTask(
        jenkinsUrl: String = "https://ci.solo-studios.ca",
        jenkinsProject: String = "job/solo-studios/job/PolyBot",
                       ) : UpdateTask {
    private val logger by getLogger()
    
    private val baseUrl = "$jenkinsUrl/$jenkinsProject"
    
    private val fuel = FuelManager().also {
        it.basePath = baseUrl
    }
    private val mapper = ObjectMapper().registerKotlinModule().also {
        it.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        it.configure(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS, true)
        it.registerModule(JavaTimeModule())
    }
    
    override suspend fun update(file: File) {
        logger.info { "Downloading the latest jar from Jenkins." }
        logger.debug { "Querying Jenkins at url $baseUrl for the latest successful build." }
        val build = fuel.get("${lastSuccessfulBuildUrl}/${jsonApiUrl}")
                .awaitObject<JenkinsBuild>(mapper)
        
        val artifact = build.artifacts.find { it.fileName.endsWith("-all.jar") } ?: error("Could not find an artifact ending in -all.jar.")
        
        logger.debug { "Latest successful build artifact found: ${artifact.fileName}, ${artifact.relativePath}" }
        
        var lastUpdate = 0L
        
        fuel.download("${lastSuccessfulBuildUrl}/$artifactUrl/${artifact.relativePath}")
                .fileDestination { _, _ -> file }
                .progress { readBytes, totalBytes ->
                    if (System.currentTimeMillis() - lastUpdate > 100) {
                        lastUpdate = System.currentTimeMillis()
                        val progress = (readBytes.toDouble() / totalBytes.toDouble()) * 100
                        logger.info { "Downloading: [%.2f%%] %s/%s".format(progress, readBytes.humanBytes(), totalBytes.humanBytes()) }
                    }
                }.awaitUnit()
        
        logger.debug { "Downloaded the latest jar artifact." }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class JenkinsBuild(
            val id: Long,
            val fullDisplayName: String,
            val timestamp: Instant,
            val url: String,
            val artifacts: List<JenkinsArtifact>
                                   )
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class JenkinsArtifact(
            @JsonInclude
            val displayPath: String,
            @JsonInclude
            val fileName: String,
            @JsonInclude
            val relativePath: String,
                                      )
    
    private fun Long.humanBytes(si: Boolean = false): String {
        val unit = if (si) 1000 else 1024
        if (this < unit)
            return "$this B"
        
        val exp = (ln(this.toDouble()) / ln(unit.toDouble())).toInt()
        val prefix = (if (si) "kMGTPE"[exp - 1] + "i" else "KMGTPE"[exp - 1]).toString()
        
        return String.format("%.1f%sB", this / unit.toDouble().pow(exp.toDouble()), prefix)
    }
    
    companion object {
        private const val artifactUrl = "artifact"
        private const val lastSuccessfulBuildUrl = "lastSuccessfulBuild"
        private const val apiUrl = "/api"
        private const val jsonApiUrl = "$apiUrl/json"
    }
    
    private suspend inline fun <reified T : Any> Request.awaitObject(mapper: ObjectMapper = com.github.kittinunf.fuel.jackson.defaultMapper): T {
        return awaitObject(jacksonDeserializerOf<T>(mapper))
    }
}
