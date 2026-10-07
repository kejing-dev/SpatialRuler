package io.embodit.spatialruler.ui.ruler.scene

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.domain.usecase.TapeGeometry
import io.embodit.spatialruler.domain.usecase.TapeRect
import io.embodit.spatialruler.platform.RulerLog
import io.embodit.spatialruler.ui.theme.RulerColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// domain <-> SDK types. The scene works in Stage metres: every entity hangs below the calibrated
// stage root, whose local space equals Stage space.
// ---------------------------------------------------------------------------------------------

internal fun Vec3.sdk(): Vector3 = Vector3(x, y, z)

internal fun Vector3.domain(): Vec3 = Vec3(x, y, z)

internal fun Quat4.sdk(): Quat = Quat(x, y, z, w)

internal fun Quat.domain(): Quat4 = Quat4(x, y, z, w)

/** Compose colours are gamma sRGB in 0..1, which is what `Color4` expects. */
internal fun Color.color4(): Color4 = Color4(red, green, blue, alpha)

internal val Entity.transformOrNull: TransformComponent?
    get() = components[TransformComponent::class.java]

internal fun Entity.setLocal(position: Vec3? = null, rotation: Quat4? = null, scale: Vec3? = null) {
    val t = transformOrNull ?: return
    if (position != null) t.setPosition(position.sdk())
    if (rotation != null) t.setQuaternion(rotation.sdk())
    if (scale != null) t.setScaleVector(scale.sdk())
}

internal fun Entity.setUniformScale(k: Float) {
    transformOrNull?.setScaleVector(Vector3(k, k, k))
}

internal fun Entity.setVisible(visible: Boolean) {
    components[ModelComponent::class.java]?.isVisible = visible
}

internal fun Entity.setMaterial(material: Material) {
    components[ModelComponent::class.java]?.materials?.set(0, material)
}

/** Overall opacity of an entity and its children (used for fades; no state depends on it). */
internal fun Entity.setOpacity(opacity: Float) {
    val existing = components[OpacityControllerComponent::class.java]
    if (existing != null) existing.opacity = opacity.coerceIn(0f, 1f)
    else components.set(OpacityControllerComponent(opacity.coerceIn(0f, 1f)))
}

/**
 * Resources that are shared by entities which come and go (materials, the pin and tape meshes).
 *
 * The SDK counts the users of a resource and releases it when its last user is destroyed: removing a
 * pin would take the pin meshes and materials away from every later pin ("can't construct
 * ModelComponent with a closed UnlitMaterial"). Shared resources are therefore made global
 * (`toGlobal()`), which keeps them until they are closed, and are closed when the scene goes away.
 */
internal class SharedResources {
    private val owned = ArrayList<Resource>()

    fun <T : Resource> keep(resource: T): T {
        resource.toGlobal()
        owned += resource
        return resource
    }

    fun close() {
        owned.forEach { runCatching { it.close() } }
        owned.clear()
    }
}

/**
 * Scene-lifetime materials. One instance per appearance, shared by every entity that uses it.
 *
 * Steel and the plastic body can be lit (physically based, lit by the passthrough-derived lighting of
 * a Mixed Stage) or unlit; [lit] chooses which set [steel], [steelShade], [shell] and [bumper] return.
 * The blade, ticks, guides and the reticle are always unlit: they must keep their exact colour, which
 * is why tone mapping is switched off for them ([toneMapped]; with it on, blade yellow `#FFC72C`
 * arrives on screen as `#D1B132`).
 */
internal class SceneMaterials(private val shared: SharedResources) {
    var lit: Boolean = true

    private val unlitAll = ArrayList<UnlitMaterial>()

    /** Tone mapping of the unlit materials (debug command `tonemap`); off is the product setting. */
    var toneMapped: Boolean = false
        set(value) {
            field = value
            unlitAll.forEach { runCatching { it.setApplyToneMapping(value) } }
        }

    val yellow: Material = unlit(RulerColors.TapeYellow)
    val yellowTwoSided: Material = unlit(RulerColors.TapeYellow, twoSided = true)
    val ink: Material = unlit(RulerColors.TapeInk, twoSided = true)
    val edge: Material = unlit(RulerColors.BladeEdge, twoSided = true)
    val red: Material = unlit(RulerColors.MeterRed, twoSided = true)
    val white: Material = unlit(RulerColors.LabelPrimaryLight, twoSided = true)
    val green: Material = unlit(RulerColors.Passable, twoSided = true)
    val darkPlate: Material = unlit(RulerColors.PlateInk.copy(alpha = 1f))
    val stale: Material = unlit(RulerColors.StaleGrey, twoSided = true)
    val reticleYellow: Material = unlit(RulerColors.TapeYellow, twoSided = true, depthTest = false)
    val reticleGreen: Material = unlit(RulerColors.Passable, twoSided = true, depthTest = false)
    val reticleWhite: Material = unlit(RulerColors.LabelPrimaryLight, twoSided = true, depthTest = false)
    val reticleAlert: Material = unlit(RulerColors.Alert, twoSided = true, depthTest = false)

    /** Contact shadows: black, see-through, never written to depth so that nothing fights with the surface. */
    val pinShadow: Material = sheer(Color.Black, 0.45f)
    val tapeShadow: Material = sheer(Color.Black, 0.36f)

    /** Dark casing under every stroke of the reticle (`bladeEdge` 70 %), never hidden by depth. */
    val reticleCasing: Material = sheer(RulerColors.BladeEdge, 0.70f, depthTest = false)

    /** The "no surface / still mapping" ring: `onInkSecondary` (white 66 %), never hidden by depth. */
    val reticleSecondary: Material = sheer(RulerColors.LabelPrimaryLight, 0.66f, depthTest = false)

    /** The ghost ring where a dragged pin was: `labelPrimaryLight` 70 %, never hidden by depth (over a `reticleCasing` ring). */
    val pinGhost: Material = sheer(RulerColors.LabelPrimaryLight, 0.70f, depthTest = false)

    /** Dark casing under the candidate threads, so that white dashes also show on a white wall. */
    val threadCasing: Material = sheer(RulerColors.BladeEdge, 0.70f)

    /** Leader from an anchor to a tag that had to stand away from it. */
    val leader: Material = sheer(RulerColors.LabelPrimaryLight, 0.66f)

    /**
     * The printed tape face: one unlit material whose texture is a four-column palette (blade yellow,
     * ink, red, rim). A whole tape is then a single mesh; each rectangle of the face samples the
     * column of its colour (see [MeshFactory.faceModel]). Creating a mesh costs a frame, so one mesh
     * per tape instead of four matters.
     */
    val tapeFace: Material = paletted(listOf(RulerColors.TapeYellow, RulerColors.TapeInk, RulerColors.MeterRed, RulerColors.BladeEdge))

    /** The same face without its colours: tapes while tracking is lost. */
    val tapeFaceStale: Material = paletted(listOf(RulerColors.StaleGrey, RulerColors.TapeInk, RulerColors.SteelShade, RulerColors.BladeEdge))

    private val steelUnlit: Material = unlit(RulerColors.Steel)
    private val steelShadeUnlit: Material = unlit(RulerColors.SteelShade)
    private val shellUnlit: Material = unlit(RulerColors.TapeYellow)
    private val bumperUnlit: Material = unlit(RulerColors.Bumper)

    /** The panel recess of the body: always flat dark, it is the backing of the glass panel. */
    val recess: Material
        get() = bumperUnlit

    // metallic 0.6: with nothing around to reflect, a fully metallic surface renders dark
    private val steelLit: PhysicallyBasedMaterial = lit(RulerColors.Steel, metallic = STEEL_METALLIC, roughness = 0.32f)
    private val steelShadeLit: PhysicallyBasedMaterial = lit(RulerColors.SteelShade, metallic = STEEL_METALLIC, roughness = 0.45f)
    private val shellLit: PhysicallyBasedMaterial = lit(RulerColors.TapeYellow, metallic = 0f, roughness = 0.45f)
    private val bumperLit: PhysicallyBasedMaterial = lit(RulerColors.Bumper, metallic = 0f, roughness = 0.7f)

    init {
        glow(STEEL_GLOW, SHELL_GLOW)
    }

    /**
     * The lit materials are shaded by the room light and tone-mapped by the Stage; a share of their own
     * colour as emission keeps them from going dull (steel) and mustard (the yellow housing).
     * Also the debug command `lit`: metallic of the steel, emission share of steel and of the housing.
     */
    fun tuneLit(steelMetallic: Float, steelGlow: Float, shellGlow: Vec3) {
        steelLit.setMetallic(steelMetallic)
        steelShadeLit.setMetallic(steelMetallic)
        glow(steelGlow, shellGlow)
    }

    private fun glow(steel: Float, shell: Vec3) {
        steelLit.setEmissiveColor(scaled(RulerColors.Steel, steel))
        steelShadeLit.setEmissiveColor(scaled(RulerColors.SteelShade, steel))
        shellLit.setEmissiveColor(Color4(shell.x, shell.y, shell.z, 1f))
    }

    private fun scaled(color: Color, k: Float): Color4 = Color4(color.red * k, color.green * k, color.blue * k, 1f)

    val steel: Material
        get() = if (lit) steelLit else steelUnlit

    val steelShade: Material
        get() = if (lit) steelShadeLit else steelShadeUnlit

    val shell: Material
        get() = if (lit) shellLit else shellUnlit

    val bumper: Material
        get() = if (lit) bumperLit else bumperUnlit

    companion object {
        const val PALETTE_COLUMNS = 4
        private const val PALETTE_CELL_PX = 8
        private const val STEEL_METALLIC = 0.6f

        /**
         * Share of its own colour the steel emits. Without it the dome of a pin samples #909699 in a console
         * capture, with it #BABDBF (design target: at least #A9AEB5).
         */
        private const val STEEL_GLOW = 0.2f

        /**
         * Emission of the yellow housing (red, green, blue). The lit housing alone samples #C2A02C (mustard);
         * with this it samples #E2C346. The emission is redder than the token and has no blue, because the
         * Stage's tone mapping pulls a bright yellow towards lemon and white (the token itself as emission
         * gave #DECC4D at 30 % and #ECE071 at 70 %). The unlit yellow of blade and washer is exact (#FFC72D).
         */
        private val SHELL_GLOW = Vec3(0.35f, 0.15f, 0f)
    }

    private fun unlit(color: Color, twoSided: Boolean = false, depthTest: Boolean = true): UnlitMaterial {
        val material =
            UnlitMaterial.create(BlendingMode.OPAQUE).apply {
                setBaseColor(color.color4())
                runCatching { setApplyToneMapping(toneMapped) }
                if (twoSided) setCullingMode(MaterialCullingMode.NONE)
                if (!depthTest) {
                    setDepthTest(false)
                    setDepthWrite(false)
                }
            }
        unlitAll += material
        return shared.keep(material)
    }

    /** A see-through unlit material: the colour is opaque, the material's opacity carries the share. */
    private fun sheer(color: Color, opacity: Float, depthTest: Boolean = true): UnlitMaterial {
        val material =
            UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(color.copy(alpha = 1f).color4())
                setOpacity(opacity)
                runCatching { setApplyToneMapping(toneMapped) }
                setCullingMode(MaterialCullingMode.NONE)
                setDepthWrite(false)
                if (!depthTest) setDepthTest(false)
            }
        unlitAll += material
        return shared.keep(material)
    }

    private fun paletted(colors: List<Color>): Material =
        try {
            val cell = PALETTE_CELL_PX
            val bitmap = Bitmap.createBitmap(cell * colors.size, cell, Bitmap.Config.ARGB_8888)
            for (i in colors.indices) for (x in 0 until cell) for (y in 0 until cell) bitmap.setPixel(i * cell + x, y, colors[i].toArgb())
            val material =
                UnlitMaterial.create(BlendingMode.OPAQUE).apply {
                    setBaseColor(Color4(1f, 1f, 1f, 1f))
                    setBaseColorTexture(TextureResource.create(bitmap))
                    runCatching { setApplyToneMapping(toneMapped) }
                    setCullingMode(MaterialCullingMode.NONE)
                }
            unlitAll += material
            shared.keep(material)
        } catch (t: Throwable) {
            // the tape stays usable (plain band, reading on the plate) if the texture cannot be made
            RulerLog.e("SCENE tape palette failed: $t", t)
            unlit(colors[0], twoSided = true)
        }

    private fun lit(color: Color, metallic: Float, roughness: Float): PhysicallyBasedMaterial =
        shared.keep(
            PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
                setBaseColor(color.color4())
                setMetallic(metallic)
                setRoughness(roughness)
            }
        )
}

/** Programmatic meshes. Meshes are built with `MeshModel` (positions, indices, normals). */
internal object MeshFactory {
    private val FRONT = Vector3(0f, 0f, 1f)
    private val UP = Vector3(0f, 1f, 0f)

    /** Flat rectangles in the local XY plane (z = 0), facing +Z. Null when there is nothing to draw. */
    fun rectModel(rects: List<TapeRect>): MeshModel? {
        if (rects.isEmpty()) return null
        val positions = ArrayList<Vector3>(rects.size * 4)
        val normals = ArrayList<Vector3>(rects.size * 4)
        val indices = ArrayList<Int>(rects.size * 6)
        for (r in rects) {
            val base = positions.size
            positions += Vector3(r.x0, r.y0, 0f)
            positions += Vector3(r.x1, r.y0, 0f)
            positions += Vector3(r.x1, r.y1, 0f)
            positions += Vector3(r.x0, r.y1, 0f)
            repeat(4) { normals += FRONT }
            indices += base
            indices += base + 1
            indices += base + 2
            indices += base
            indices += base + 2
            indices += base + 3
        }
        return MeshModel(positions = positions, triangleIndices = indices, normals = normals)
    }

    /**
     * The whole tape face as one mesh in the local XY plane, facing +Z. Every rectangle carries the
     * texture coordinate of its colour's palette column (0 blade, 1 ink, 2 red, 3 rim).
     */
    fun faceModel(g: TapeGeometry): MeshModel? {
        val count = g.band.size + g.ink.size + g.red.size + g.edge.size
        if (count == 0) return null
        val positions = ArrayList<Vector3>(count * 4)
        val normals = ArrayList<Vector3>(count * 4)
        val uvs = ArrayList<Vector2>(count * 4)
        val indices = ArrayList<Int>(count * 6)
        fun add(rects: List<TapeRect>, column: Int) {
            val uv = Vector2((column + 0.5f) / SceneMaterials.PALETTE_COLUMNS, 0.5f)
            for (r in rects) {
                val base = positions.size
                positions += Vector3(r.x0, r.y0, 0f)
                positions += Vector3(r.x1, r.y0, 0f)
                positions += Vector3(r.x1, r.y1, 0f)
                positions += Vector3(r.x0, r.y1, 0f)
                repeat(4) {
                    normals += FRONT
                    uvs += uv
                }
                indices += base
                indices += base + 1
                indices += base + 2
                indices += base
                indices += base + 2
                indices += base + 3
            }
        }
        add(g.band, 0)
        add(g.ink, 1)
        add(g.red, 2)
        add(g.edge, 3)
        return MeshModel(positions = positions, triangleIndices = indices, normals = normals, uv0 = uvs)
    }

    /** A printed piece of blade as a shared mesh (see `LayoutTapeUseCase.segment`). */
    fun faceMesh(g: TapeGeometry, length: Float, halfWidth: Float, name: String): MeshResource =
        MeshResource.createWithMeshModel(model = faceModel(g)!!, bounds = rectBounds(length, halfWidth), name = name)

    /** Strip x 0..1, y -0.5..0.5 facing +Z: scaled to any length and width (growing blade, outlines, shadows). */
    fun unitBand(name: String): MeshResource =
        MeshResource.createWithMeshModel(
            model = rectModel(listOf(TapeRect(0f, 1f, -0.5f, 0.5f)))!!,
            bounds = BoundingBox(center = Vector3(0.5f, 0f, 0f), halfExtent = Vector3(0.5f, 0.5f, 0.002f)),
            name = name,
        )

    /** The unit strip cut into [dashes] equal dashes: a dashed line of any length when scaled. */
    fun unitDashes(name: String, dashes: Int, fill: Float = 0.5f): MeshResource {
        val period = 1f / dashes
        val rects = List(dashes) { i -> TapeRect(i * period + period * (1f - fill) / 2f, i * period + period * (1f + fill) / 2f, -0.5f, 0.5f) }
        return MeshResource.createWithMeshModel(
            model = rectModel(rects)!!,
            bounds = BoundingBox(center = Vector3(0.5f, 0f, 0f), halfExtent = Vector3(0.5f, 0.5f, 0.002f)),
            name = name,
        )
    }

    fun rectBounds(lengthM: Float, halfWidthM: Float): BoundingBox =
        BoundingBox(center = Vector3(lengthM / 2f, 0f, 0f), halfExtent = Vector3(lengthM / 2f + 0.001f, halfWidthM, 0.002f))

    /**
     * Flat ring in the local XZ plane (y = 0), facing +Y. With [dashes] > 0 only that many arcs are
     * drawn, each covering [dashFill] of its slot (the "pinning" and "unsaved" collars).
     */
    fun ring(inner: Float, outer: Float, name: String, dashes: Int = 0, dashFill: Float = 0.6f, segments: Int = 48): MeshResource {
        val positions = ArrayList<Vector3>()
        val normals = ArrayList<Vector3>()
        val indices = ArrayList<Int>()
        fun arc(a0: Double, a1: Double, steps: Int) {
            for (i in 0 until steps) {
                val s = a0 + (a1 - a0) * i / steps
                val e = a0 + (a1 - a0) * (i + 1) / steps
                val base = positions.size
                positions += Vector3((cos(s) * inner).toFloat(), 0f, (sin(s) * inner).toFloat())
                positions += Vector3((cos(s) * outer).toFloat(), 0f, (sin(s) * outer).toFloat())
                positions += Vector3((cos(e) * outer).toFloat(), 0f, (sin(e) * outer).toFloat())
                positions += Vector3((cos(e) * inner).toFloat(), 0f, (sin(e) * inner).toFloat())
                repeat(4) { normals += UP }
                // counter-clockwise seen from +Y
                indices += base
                indices += base + 2
                indices += base + 1
                indices += base
                indices += base + 3
                indices += base + 2
            }
        }
        if (dashes <= 0) {
            arc(0.0, 2 * PI, segments)
        } else {
            val slot = 2 * PI / dashes
            for (d in 0 until dashes) arc(d * slot, d * slot + slot * dashFill, 3)
        }
        val model = MeshModel(positions = positions, triangleIndices = indices, normals = normals)
        val bounds = BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(outer, 0.001f, outer))
        return MeshResource.createWithMeshModel(model = model, bounds = bounds, name = name)
    }

    /** Solid disc in the local XZ plane, facing +Y. */
    fun disc(radius: Float, name: String, segments: Int = 40): MeshResource {
        val positions = ArrayList<Vector3>(segments + 1)
        val normals = ArrayList<Vector3>(segments + 1)
        val indices = ArrayList<Int>(segments * 3)
        positions += Vector3(0f, 0f, 0f)
        normals += UP
        for (i in 0 until segments) {
            val a = 2 * PI * i / segments
            positions += Vector3((cos(a) * radius).toFloat(), 0f, (sin(a) * radius).toFloat())
            normals += UP
        }
        for (i in 0 until segments) {
            indices += 0
            indices += 1 + (i + 1) % segments
            indices += 1 + i
        }
        val model = MeshModel(positions = positions, triangleIndices = indices, normals = normals)
        val bounds = BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(radius, 0.001f, radius))
        return MeshResource.createWithMeshModel(model = model, bounds = bounds, name = name)
    }

    /**
     * Four radial marks in the local XZ plane, facing +Y, at 0 / 90 / 180 / 270 degrees: each [length]
     * long and [width] wide, its outer end at [outerRadius] (the index marks on a pin's washer).
     */
    fun radialMarks(outerRadius: Float, length: Float, width: Float, name: String): MeshResource {
        val positions = ArrayList<Vector3>()
        val normals = ArrayList<Vector3>()
        val indices = ArrayList<Int>()
        fun quad(x0: Float, x1: Float, z0: Float, z1: Float) {
            val base = positions.size
            positions += Vector3(x0, 0f, z0)
            positions += Vector3(x1, 0f, z0)
            positions += Vector3(x1, 0f, z1)
            positions += Vector3(x0, 0f, z1)
            repeat(4) { normals += UP }
            indices += base
            indices += base + 2
            indices += base + 1
            indices += base
            indices += base + 3
            indices += base + 2
        }
        val inner = outerRadius - length
        val h = width / 2f
        quad(inner, outerRadius, -h, h)
        quad(-outerRadius, -inner, -h, h)
        quad(-h, h, inner, outerRadius)
        quad(-h, h, -outerRadius, -inner)
        val model = MeshModel(positions = positions, triangleIndices = indices, normals = normals)
        val bounds = BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(outerRadius, 0.001f, outerRadius))
        return MeshResource.createWithMeshModel(model = model, bounds = bounds, name = name)
    }

    /** A plus sign in the local XZ plane, facing +Y, with a gap in the middle (reticle cross). */
    fun cross(halfLength: Float, halfWidth: Float, name: String, gap: Float = 0f): MeshResource {
        val positions = ArrayList<Vector3>()
        val normals = ArrayList<Vector3>()
        val indices = ArrayList<Int>()
        fun quad(x0: Float, x1: Float, z0: Float, z1: Float) {
            val base = positions.size
            positions += Vector3(x0, 0f, z0)
            positions += Vector3(x1, 0f, z0)
            positions += Vector3(x1, 0f, z1)
            positions += Vector3(x0, 0f, z1)
            repeat(4) { normals += UP }
            indices += base
            indices += base + 2
            indices += base + 1
            indices += base
            indices += base + 3
            indices += base + 2
        }
        if (gap <= 0f) {
            quad(-halfLength, halfLength, -halfWidth, halfWidth)
            quad(-halfWidth, halfWidth, -halfLength, halfLength)
        } else {
            quad(gap, halfLength, -halfWidth, halfWidth)
            quad(-halfLength, -gap, -halfWidth, halfWidth)
            quad(-halfWidth, halfWidth, gap, halfLength)
            quad(-halfWidth, halfWidth, -halfLength, -gap)
        }
        val model = MeshModel(positions = positions, triangleIndices = indices, normals = normals)
        val bounds = BoundingBox(center = Vector3(0f, 0f, 0f), halfExtent = Vector3(halfLength, 0.001f, halfLength))
        return MeshResource.createWithMeshModel(model = model, bounds = bounds, name = name)
    }
}

/**
 * Creating or replacing a mesh takes about one rendered frame (0.4 s each at the emulator's idle
 * 2 - 4 fps). The scene therefore allows a fixed number of mesh builds per frame; whoever does not
 * get one keeps drawing its placeholder and asks again in the next frame.
 */
internal class MeshBudget(private val perFrame: Int) {
    private var left = perFrame

    fun reset() {
        left = perFrame
    }

    fun take(): Boolean {
        if (left <= 0) return false
        left--
        return true
    }
}

/** A mesh that is rebuilt while the scene lives (the printed face of a tape). */
internal class DynamicMesh(private val parent: Entity, private var material: Material, private val name: String) {
    private var mesh: MeshResource? = null
    private var entity: Entity? = null

    fun setMaterial(value: Material) {
        if (value === material) return
        material = value
        entity?.setMaterial(value)
    }

    /** Replaces the geometry; an empty model hides the part. */
    fun update(model: MeshModel?, bounds: BoundingBox) {
        if (model == null) {
            entity?.setVisible(false)
            return
        }
        val current = mesh
        val e = entity
        if (current == null || e == null) {
            val created = MeshResource.createWithMeshModel(model = model, bounds = bounds, name = name)
            val child = ModelEntity(created, material)
            parent.addChild(child)
            mesh = created
            entity = child
            return
        }
        val replaced = runCatching { current.replaceWithMeshModel(model = model, bounds = bounds) }.getOrDefault(false)
        if (replaced) {
            e.components[ModelComponent::class.java]?.let {
                it.mesh = current
                it.isVisible = true
            }
        } else {
            // replacing in place was refused: build a fresh mesh and entity
            runCatching { e.destroy(true) }
            val created = MeshResource.createWithMeshModel(model = model, bounds = bounds, name = name)
            val child = ModelEntity(created, material)
            parent.addChild(child)
            mesh = created
            entity = child
        }
    }
}

/**
 * A straight strip between two Stage points that turns its face to the viewer: the shared unit band
 * scaled to the length and width (leaders, stems, seat marks). Showing one builds no mesh.
 */
internal class Strip(parent: Entity, mesh: MeshResource, material: Material, name: String) {
    private val entity: Entity = ModelEntity(mesh, material)
    private var on = true

    init {
        runCatching { entity.setName(name) }
        parent.addChild(entity)
        hide()
    }

    /** From [from] to [to], [width] wide, its face along [faceNormal] (made square to the strip). */
    fun draw(from: Vec3, to: Vec3, width: Float, faceNormal: Vec3) {
        val full = to - from
        val length = full.length
        if (length < 1e-4f) {
            hide()
            return
        }
        val dir = full / length
        var z = faceNormal.rejectFrom(dir)
        if (z.length < 1e-4f) z = Vec3.UP.rejectFrom(dir)
        if (z.length < 1e-4f) z = Vec3.RIGHT.rejectFrom(dir)
        z = z.normalized()
        entity.setLocal(position = from, rotation = Quat4.fromBasis(dir, z.cross(dir).normalized(), z), scale = Vec3(length, width, 1f))
        if (!on) {
            entity.enabled = true
            on = true
        }
    }

    fun setOpacity(value: Float) = entity.setOpacity(value)

    fun setMaterial(value: Material) = entity.setMaterial(value)

    fun hide() {
        if (on) {
            entity.enabled = false
            on = false
        }
    }

    fun destroy() {
        runCatching { entity.destroy(true) }
    }
}
