package cuitteacon26.thestreetism.client

import cuitteacon26.thestreetism.Thestreetism
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object GraffitiTextures {
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val loaded = ConcurrentHashMap<String, Identifier>()

    private val FALLBACK_TEXTURE =
        Identifier.fromNamespaceAndPath(
            Thestreetism.ID,
            "textures/graffiti/empty.png"
        )

    fun resolve(key: String): Identifier {
        return when {
            key.startsWith("remote:") ->
                resolveRemote(key.removePrefix("remote:"))

            key.startsWith("local:") ->
                resolveLocal(key.removePrefix("local:"))

            else ->
                resolveLocal(key)
        }
    }

    private fun resolveLocal(name: String): Identifier {
        val parsed = Identifier.tryParse(name)

        if (parsed != null) {
            if (
                parsed.path.startsWith("textures/")
                || parsed.path.endsWith(".png")
            ) {
                return parsed
            }

            return Identifier.fromNamespaceAndPath(
                parsed.namespace,
                "textures/graffiti/${parsed.path}.png"
            )
        }

        return Identifier.fromNamespaceAndPath(
            Thestreetism.ID,
            "textures/graffiti/$name.png"
        )
    }

    private fun resolveRemote(url: String): Identifier {
        loaded[url]?.let {
            return it
        }

        val textureId =
            Identifier.fromNamespaceAndPath(
                Thestreetism.ID,
                "remote_graffiti/${sha256(url)}"
            )

        if (loading.add(url)) {
            CompletableFuture
                .supplyAsync {
                    download(url)
                }
                .thenAccept { image ->
                    Minecraft.getInstance().execute {
                        try {
                            Minecraft.getInstance()
                                .textureManager
                                .register(
                                    textureId,
                                    DynamicTexture(
                                        { "remote graffiti $url" },
                                        image
                                    )
                                )

                            loaded[url] = textureId

                            Minecraft.getInstance().levelRenderer.allChanged()

                            Thestreetism.LOGGER.info(
                                "Loaded remote graffiti texture {}",
                                url
                            )
                        } catch (e: Exception) {
                            Thestreetism.LOGGER.error(
                                "Failed to register remote graffiti texture {}",
                                url,
                                e
                            )
                        } finally {
                            loading.remove(url)
                        }
                    }
                }
                .exceptionally { error ->
                    loading.remove(url)

                    Thestreetism.LOGGER.warn(
                        "Failed to load remote graffiti texture {}",
                        url,
                        error
                    )

                    null
                }
        }

        return FALLBACK_TEXTURE
    }

    private fun download(url: String): NativeImage {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") {
            "Only HTTP/HTTPS URLs are supported"
        }

        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.requestMethod = "GET"
        connection.doInput = true

        return try {
            connection.inputStream.buffered().use { stream ->
                NativeImage.read(LimitedInputStream(stream, MAX_DOWNLOAD_BYTES))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(value: String): String {
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") {
            "%02x".format(Locale.ROOT, it)
        }
    }

    private class LimitedInputStream(
        private val delegate: java.io.InputStream,
        private val maxBytes: Long,
    ) : java.io.InputStream() {
        private var bytesRead = 0L

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) {
                incrementBytesRead(1)
            }
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val value = delegate.read(b, off, len)
            if (value > 0) {
                incrementBytesRead(value.toLong())
            }
            return value
        }

        override fun close() = delegate.close()

        private fun incrementBytesRead(count: Long) {
            bytesRead += count
            check(bytesRead <= maxBytes) {
                "Remote graffiti texture exceeds $maxBytes bytes"
            }
        }
    }

    private const val CONNECT_TIMEOUT_MILLIS = 5_000
    private const val READ_TIMEOUT_MILLIS = 10_000
    private const val MAX_DOWNLOAD_BYTES = 8L * 1024L * 1024L
    private const val USER_AGENT = "thestreetism-graffiti-loader"
}
