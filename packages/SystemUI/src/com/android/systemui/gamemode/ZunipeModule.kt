package com.android.systemui.gamemode

import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.MuteTile
import com.android.systemui.qs.tiles.PerfBoostTile
import com.android.systemui.qs.tiles.PerfHudTile
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.res.R
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
interface ZunipeModule {
    @Binds
    @IntoMap
    @StringKey(PerfHudTile.TILE_SPEC)
    fun bindPerfHudTile(perfHudTile: PerfHudTile): QSTileImpl<*>

    @Binds
    @IntoMap
    @StringKey(MuteTile.TILE_SPEC)
    fun bindMuteTile(muteTile: MuteTile): QSTileImpl<*>

    @Binds
    @IntoMap
    @StringKey(PerfBoostTile.TILE_SPEC)
    fun bindPerfBoostTile(perfBoostTile: PerfBoostTile): QSTileImpl<*>

//    @Binds
//    @IntoMap
//    @StringKey(PERF_TILE_SPEC)
//    fun providePerfAvailabilityInteractor(
//        impl: PerfTileDataInteractor
//    ): QSTileAvailabilityInteractor
//
//    @Binds
//    @IntoMap
//    @StringKey(MUTE_TILE_SPEC)
//    fun provideMuteAvailabilityInteractor(
//        impl: MuteTileDataInteractor
//    ): QSTileAvailabilityInteractor

    companion object {
        private const val PERF_TILE_SPEC = "perf_hud"
        private const val MUTE_TILE_SPEC = "mute"
        private const val PERF_BOOST_TILE_SPEC = "perf_boost"

        @Provides
        @IntoMap
        @StringKey(PERF_TILE_SPEC)
        fun providePerfTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(PERF_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.qs_perf_hud_icon,
                        labelRes = R.string.perf_hud_switch_title,
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

        @Provides
        @IntoMap
        @StringKey(MUTE_TILE_SPEC)
        fun provideMuteTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(MUTE_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.qs_perf_hud_icon,
                        labelRes = R.string.mute_switch_title,
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

        @Provides
        @IntoMap
        @StringKey(PERF_BOOST_TILE_SPEC)
        fun providePerfBoostTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(PERF_BOOST_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.qs_perf_boost_icon,
                        labelRes = R.string.perf_boost_switch_title,
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )

//        @Provides
//        @IntoMap
//        @StringKey(PERF_TILE_SPEC)
//        fun providePerfTileViewModel(
//            factory: QSTileViewModelFactory.Static<PerfModel>,
//            mapper: PerfTileMapper,
//            stateInteractor: PerfTileDataInteractor,
//            userActionInteractor: PerfTileUserActionInteractor,
//        ): QSTileViewModel =
//            factory.create(
//                TileSpec.create(PERF_TILE_SPEC),
//                userActionInteractor,
//                stateInteractor,
//                mapper,
//            )
//
//        @Provides
//        @IntoMap
//        @StringKey(MUTE_TILE_SPEC)
//        fun provideMuteTileViewModel(
//            factory: QSTileViewModelFactory.Static<MuteModel>,
//            mapper: MuteTileMapper,
//            stateInteractor: MuteTileDataInteractor,
//            userActionInteractor: MuteTileUserActionInteractor,
//        ): QSTileViewModel =
//            factory.create(
//                TileSpec.create(MUTE_TILE_SPEC),
//                userActionInteractor,
//                stateInteractor,
//                mapper,
//            )
    }
}