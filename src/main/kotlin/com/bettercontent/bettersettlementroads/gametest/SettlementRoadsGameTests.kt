package com.bettercontent.bettersettlementroads.gametest

import com.bettercontent.bettersettlementroads.SettlementRoadsMod
import com.bettercontent.bettersettlementroads.command.DebugScenarioBuilder
import com.bettercontent.bettersettlementroads.command.DebugScenarioId
import com.bettercontent.bettersettlementroads.command.createDefinition
import com.bettercontent.bettersettlementroads.data.PlannedRoadNetwork
import com.bettercontent.bettersettlementroads.data.SettlementRoadsSavedData
import com.bettercontent.bettersettlementroads.debug.DebugPlanPlacer
import com.bettercontent.bettersettlementroads.planner.PlannerConfig
import com.bettercontent.bettersettlementroads.planner.model.PathSegment
import com.bettercontent.bettersettlementroads.runtime.SettlementRoadsRuntime
import com.bettercontent.bettersettlementroads.runtime.SurfaceSampler
import com.bettercontent.bettersettlementroads.runtime.WorldNetworkPlacer
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.ChunkPos
import net.minecraftforge.event.RegisterGameTestsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.gametest.GameTestHolder

@GameTestHolder(SettlementRoadsMod.MOD_ID)
@Mod.EventBusSubscriber(modid = SettlementRoadsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
object SettlementRoadsGameTests {
    private val config = PlannerConfig(allowWaterBridges = true)

    @JvmStatic
    @SubscribeEvent
    fun register(event: RegisterGameTestsEvent) {
        event.register(SettlementRoadsGameTests::class.java)
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun grassyRiverCrossingProducesBridge(helper: GameTestHelper) {
        val (_, _, segments, _) = spawnAndPlace(helper, DebugScenarioId.GRASSY_RIVER_CROSSING)

        val bridges = segments.filterIsInstance<PathSegment.Bridge>()
        helper.assertTrue(bridges.isNotEmpty(), "Grassy river crossing should produce at least one bridge segment")
        val bridge = bridges.first()
        helper.assertTrue(bridge.supports.isNotEmpty(), "Bridge should create support columns")
        helper.assertTrue(bridge.supports.all { it.reachedSolid }, "Bridge supports should reach solid terrain")
        helper.assertTrue(
            helper.level.getBlockState(bridge.blocks.first()).`is`(Blocks.STONE_BRICKS),
            "Bridge deck should place stone bricks at ${bridge.blocks.first()}"
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun tooWideRiverDetoursWithoutBridge(helper: GameTestHelper) {
        val (_, _, segments, _) = spawnAndPlace(helper, DebugScenarioId.TOO_WIDE_RIVER)
        helper.assertTrue(segments.none { it is PathSegment.Bridge }, "Wide-river scenario should detour instead of bridging")
        helper.assertTrue(
            segments.filterIsInstance<PathSegment.Ground>().flatMap { it.blocks }.any { it.z != helper.absolutePos(BlockPos.ZERO).z },
            "Wide-river scenario should take a detour off the straight center line"
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun rockyCrossingUsesGravelAndBridge(helper: GameTestHelper) {
        val (_, _, segments, _) = spawnAndPlace(helper, DebugScenarioId.ROCKY_CROSSING)

        val firstGround = segments.filterIsInstance<PathSegment.Ground>().first()
        helper.assertTrue(
            firstGround.blocks.any { pos -> helper.level.getBlockState(pos).block == Blocks.GRAVEL },
            "Rocky crossing should use gravel for ground segments"
        )
        helper.assertTrue(segments.any { it is PathSegment.Bridge }, "Rocky crossing should still use a bridge")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun caveUnderRiverContinuesSupportsToSolid(helper: GameTestHelper) {
        val (_, _, segments, _) = spawnAndPlace(helper, DebugScenarioId.CAVE_UNDER_RIVERBED)
        val bridge = segments.filterIsInstance<PathSegment.Bridge>().first()

        helper.assertTrue(bridge.supports.isNotEmpty(), "Cave scenario bridge should create support columns")
        helper.assertTrue(
            bridge.supports.all { support -> support.reachedSolid && support.toY <= support.fromY - 4 },
            "Cave scenario supports should continue past the hollow riverbed"
        )
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun rerunPlacementIsIdempotent(helper: GameTestHelper) {
        val (_, network, _, placedOnce) = spawnAndPlace(helper, DebugScenarioId.FLAT_GRASSY_TWINS)
        val placedTwice = DebugPlanPlacer.place(
            level = helper.level,
            rings = network.rings.map { it.perimeter },
            segments = network.clusters.flatMap { it.connections }.flatMap { it.segments },
            isGrassy = true
        )

        helper.assertTrue(placedOnce > 0, "Initial placement should change the world")
        helper.assertTrue(placedTwice == 0, "Second placement should be idempotent")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun chunkBoundaryScenarioPlacesRoadAcrossBothChunks(helper: GameTestHelper) {
        val (_, network, segments, placed) = spawnAndPlace(helper, DebugScenarioId.CHUNK_BOUNDARY_SPLIT)
        val segmentChunks = segments
            .flatMap {
                when (it) {
                    is PathSegment.Bridge -> it.blocks
                    is PathSegment.Ground -> it.blocks
                }
            }
            .map { it.x shr 4 }
            .toSet()

        helper.assertTrue(placed > 0, "Chunk-boundary scenario should place road blocks")
        helper.assertTrue(network.chunkStamps.map { it.chunkX }.toSet().size >= 2, "Plan should record placement stamps in both chunks")
        helper.assertTrue(segmentChunks.size >= 2, "Road segments should cross the chunk boundary")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "blank", timeoutTicks = 100)
    fun registeredTickRebuildsOnFirstDirtyTick(helper: GameTestHelper) {
        val level = helper.level
        val saved = SettlementRoadsSavedData.get(level)
        val original = saved.state
        val stale = PlannedRoadNetwork.debugScenario(
            helper.absolutePos(BlockPos(0, 2, 0)), DebugScenarioId.FLAT_GRASSY_TWINS, config
        )
        val completion = "finished-before-observation"
        saved.update { it.copy(worldStructures = emptyList(), worldNetwork = stale.copy(appliedSegments = setOf(completion))) }
        SettlementRoadsRuntime.markDirty(level)

        // The mod's registered level-tick listener drains the dirty work; this test
        // does not invoke rebuildFromLoadedChunks or the placer directly.
        helper.succeedWhen {
            val current = saved.state.worldNetwork
            helper.assertTrue(current.structures.isEmpty(), "First dirty tick must rebuild the stale network")
            helper.assertTrue(completion in current.appliedSegments, "Completed segments survive an unobserved scan")
            saved.update { original }
        }
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun productionPlacementPreservesInterveningConstruction(helper: GameTestHelper) {
        val origin = helper.absolutePos(BlockPos(0, 2, 0))
        DebugScenarioBuilder.spawn(helper.level, DebugScenarioId.FLAT_GRASSY_TWINS.createDefinition(origin, config))
        val network = PlannedRoadNetwork.debugScenario(origin, DebugScenarioId.FLAT_GRASSY_TWINS, config)
        val ground = network.clusters.flatMap { it.connections }
            .flatMap { it.segments }.filterIsInstance<PathSegment.Ground>().first()
        val protected = SurfaceSampler.groundPos(helper.level, ground.blocks[ground.blocks.size / 2].x, ground.blocks[ground.blocks.size / 2].z)
        helper.level.setBlockAndUpdate(protected, Blocks.CRAFTING_TABLE.defaultBlockState())
        val loaded = (-4..4).flatMap { dx ->
            (-4..4).map { dz -> ChunkPos.asLong((origin.x shr 4) + dx, (origin.z shr 4) + dz) }
        }.toSet()

        val result = WorldNetworkPlacer.placeAvailable(helper.level, network, loaded, config)
        helper.assertTrue(result.appliedSegments.isNotEmpty(), "Production placement must attempt the loaded route")
        helper.assertTrue(helper.level.getBlockState(protected).`is`(Blocks.CRAFTING_TABLE), "Constructed block must survive road placement")
        val completed = network.copy(appliedSegments = result.appliedSegments)
        val removed = SurfaceSampler.groundPos(helper.level, ground.blocks.first().x, ground.blocks.first().z)
        helper.level.setBlockAndUpdate(removed, Blocks.AIR.defaultBlockState())
        WorldNetworkPlacer.placeAvailable(helper.level, completed, loaded, config)
        helper.assertTrue(helper.level.getBlockState(removed).isAir, "Completed road must not repair a later edit")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "blank")
    fun registeredClusteredPlacementCodecPreservesFieldsAndNativeLevelSeed(helper: GameTestHelper) {
        val ops = com.mojang.serialization.JsonOps.INSTANCE
        val codec = net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.CODEC
        val authored = com.google.gson.JsonParser.parseString("""
            {"type":"better_settlement_roads:clustered_spread","salt":381311,
             "locate_offset":[3,0,-2],"cluster_spacing":64,"cluster_radius":8,"baseline_spacing":64}
        """.trimIndent())
        val placement = codec.parse(ops, authored).result().orElseThrow()
        helper.assertTrue(placement is com.bettercontent.bettersettlementroads.worldgen.ClusteredSpreadStructurePlacement,
            "Registered dispatch codec must resolve our native placement type")
        placement as com.bettercontent.bettersettlementroads.worldgen.ClusteredSpreadStructurePlacement
        helper.assertTrue(placement.type() === com.bettercontent.bettersettlementroads.worldgen.SettlementRoadsWorldgen.CLUSTERED_SPREAD.get(),
            "Decoded placement must use the registered type")
        helper.assertTrue(placement.clusterSpacing() == 64 && placement.clusterRadius() == 8 && placement.baselineSpacing() == 64,
            "Native placement codec must preserve authored fields")
        val encoded = codec.encodeStart(ops, placement).result().orElseThrow()
        helper.assertTrue(encoded.asJsonObject.get("salt").asInt == authored.asJsonObject.get("salt").asInt, "Native codec must preserve the placement salt")
        helper.assertTrue(encoded.asJsonObject.get("type").asString == "better_settlement_roads:clustered_spread",
            "Dispatch encoding must retain the registered type ID")
        helper.assertTrue(encoded.asJsonObject.getAsJsonArray("locate_offset") == authored.asJsonObject.getAsJsonArray("locate_offset"),
            "Base placement codec fields must round-trip too")
        val decodedAgain = codec.parse(ops, encoded).result().orElseThrow()
        helper.assertTrue(codec.encodeStart(ops, decodedAgain).result().orElseThrow() == encoded,
            "Actual dispatch codec must round-trip stably")
        val invalid = authored.deepCopy().asJsonObject.also { it.addProperty("cluster_radius", 0) }
        val invalidRejected = try {
            codec.parse(ops, invalid).error().isPresent
        } catch (_: IllegalArgumentException) {
            true // The placement constructor also enforces geometry after codec partial decoding.
        }
        helper.assertTrue(invalidRejected, "Codec must reject an out-of-range radius")

        val level = helper.level
        val generator = level.chunkSource.generator
        fun state(seed: Long) = net.minecraft.world.level.chunk.ChunkGeneratorStructureState.createForFlat(
            level.chunkSource.randomState(), seed, generator.biomeSource,
            java.util.stream.Stream.empty<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.StructureSet>>())
        val seeds = listOf(level.seed, level.seed xor 0x5EEDL)
        val fields = seeds.map { seed ->
            val nativeState = state(seed)
            val selected = mutableSetOf<Pair<Int, Int>>()
            for (x in -64 until 64) for (z in -64 until 64) {
                val expected = com.bettercontent.bettersettlementroads.worldgen.ClusteredSpreadPolicy.site(
                    seed, x, z, authored.asJsonObject.get("salt").asInt, 0, placement.clusterSpacing(), placement.clusterRadius(), placement.baselineSpacing(), 2) != null
                val actual = decodedAgain.isStructureChunk(nativeState, x, z)
                helper.assertTrue(actual == expected, "Native placement must forward its structure state's level seed at $x,$z")
                if (actual) selected += x to z
            }
            helper.assertTrue(selected.isNotEmpty() && selected.size < 128 * 128, "Native field must exercise admitted and rejected chunks")
            selected
        }
        helper.assertTrue(fields[0] != fields[1], "Changing the native structure-state seed must change the placement field")
        helper.succeed()
    }

    private fun spawnAndPlace(
        helper: GameTestHelper,
        scenarioId: DebugScenarioId
    ): SpawnedScenario {
        val origin = helper.absolutePos(BlockPos(0, 2, 0))
        val definition = scenarioId.createDefinition(origin, config)
        DebugScenarioBuilder.spawn(helper.level, definition)
        val network = PlannedRoadNetwork.debugScenario(origin, scenarioId, config)
        val segments = network.clusters.flatMap { it.connections }.flatMap { it.segments }
        val placed = DebugPlanPlacer.place(
            level = helper.level,
            rings = network.rings.map { it.perimeter },
            segments = segments,
            isGrassy = definition.isGrassy
        )
        return SpawnedScenario(definition, network, segments, placed)
    }

    private data class SpawnedScenario(
        val definition: com.bettercontent.bettersettlementroads.command.DebugScenarioDefinition,
        val network: PlannedRoadNetwork,
        val segments: List<PathSegment>,
        val placedBlocks: Int
    )
}
