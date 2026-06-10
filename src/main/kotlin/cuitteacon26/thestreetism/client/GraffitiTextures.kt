package cuitteacon26.thestreetism.client

import cuitteacon26.thestreetism.Thestreetism
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
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

                            Minecraft.getInstance().levelRenderer?.allChanged()

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

        require(
            uri.scheme == "http" ||
                    uri.scheme == "https"
        ) {
            "Only HTTP/HTTPS URLs are supported"
        }

        return uri.toURL()
            .openStream()
            .use(NativeImage::read)
    }

    private fun sha256(value: String): String {
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") {
            "%02x".format(Locale.ROOT, it)
        }
    }
}