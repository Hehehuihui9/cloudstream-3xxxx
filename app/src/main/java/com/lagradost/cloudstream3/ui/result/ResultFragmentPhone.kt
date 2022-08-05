package com.lagradost.cloudstream3.ui.result

import android.app.Dialog
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.discord.panels.OverlappingPanelsLayout
import com.discord.panels.PanelsChildGestureRegionObserver
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.APIHolder.updateHasTrailers
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Some
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.utils.AppUtils.openBrowser
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialogInstant
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import kotlinx.android.synthetic.main.fragment_result.*
import kotlinx.android.synthetic.main.fragment_result_swipe.*
import kotlinx.android.synthetic.main.result_recommendations.*
import kotlinx.android.synthetic.main.trailer_custom_layout.*

class ResultFragmentPhone : ResultFragment() {
    var currentTrailers: List<ExtractorLink> = emptyList()
    var currentTrailerIndex = 0

    override fun nextMirror() {
        currentTrailerIndex++
        loadTrailer()
    }

    override fun hasNextMirror(): Boolean {
        return currentTrailerIndex + 1 < currentTrailers.size
    }

    override fun playerError(exception: Exception) {
        if (player.getIsPlaying()) { // because we dont want random toasts in player
            super.playerError(exception)
        } else {
            nextMirror()
        }
    }

    private fun loadTrailer(index: Int? = null) {
        val isSuccess =
            currentTrailers.getOrNull(index ?: currentTrailerIndex)?.let { trailer ->
                context?.let { ctx ->
                    player.onPause()
                    player.loadPlayer(
                        ctx,
                        false,
                        trailer,
                        null,
                        startPosition = 0L,
                        subtitles = emptySet(),
                        subtitle = null,
                        autoPlay = false
                    )
                    true
                } ?: run {
                    false
                }
            } ?: run {
                false
            }
        result_trailer_loading?.isVisible = isSuccess
        result_smallscreen_holder?.isVisible = !isSuccess && !isFullScreenPlayer

        // We don't want the trailer to be focusable if it's not visible
        result_smallscreen_holder?.descendantFocusability = if (isSuccess) {
            ViewGroup.FOCUS_AFTER_DESCENDANTS
        } else {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        result_fullscreen_holder?.isVisible = !isSuccess && isFullScreenPlayer
    }

    override fun setTrailers(trailers: List<ExtractorLink>?) {
        context?.updateHasTrailers()
        if (!LoadResponse.isTrailersEnabled) return
        currentTrailers = trailers?.sortedBy { -it.quality } ?: emptyList()
        loadTrailer()
    }

    override fun onDestroyView() {
        //somehow this still leaks and I dont know why????
        // todo look at https://github.com/discord/OverlappingPanels/blob/70b4a7cf43c6771873b1e091029d332896d41a1a/sample_app/src/main/java/com/discord/sampleapp/MainActivity.kt
        PanelsChildGestureRegionObserver.Provider.get().let { obs ->
            result_cast_items?.let {
                obs.unregister(it)
            }
            obs.removeGestureRegionsUpdateListener(this)
        }

        super.onDestroyView()
    }

    var loadingDialog: Dialog? = null
    var popupDialog: Dialog? = null

    /**
     * Sets next focus to allow navigation up and down between 2 views
     * if either of them is null nothing happens.
     **/
    private fun setFocusUpAndDown(upper: View?, down: View?) {
        if (upper == null || down == null) return
        upper.nextFocusDownId = down.id
        down.nextFocusUpId = upper.id
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        player_open_source?.setOnClickListener {
            currentTrailers.getOrNull(currentTrailerIndex)?.let {
                context?.openBrowser(it.url)
            }
        }
        result_recommendations?.spanCount = 3
        result_overlapping_panels?.setStartPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)
        result_overlapping_panels?.setEndPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)

        result_recommendations?.adapter =
            SearchAdapter(
                ArrayList(),
                result_recommendations,
            ) { callback ->
                SearchHelper.handleSearchClickCallback(activity, callback)
            }
        PanelsChildGestureRegionObserver.Provider.get().addGestureRegionsUpdateListener(this)

        result_cast_items?.let {
            PanelsChildGestureRegionObserver.Provider.get().register(it)
        }


        result_back?.setOnClickListener {
            activity?.popCurrentPage()
        }

        result_bookmark_button?.setOnClickListener {
            it.popupMenuNoIcons(
                items = WatchType.values()
                    .map { watchType -> Pair(watchType.internalId, watchType.stringRes) },
                //.map { watchType -> Triple(watchType.internalId, watchType.iconRes, watchType.stringRes) },
            ) {
                viewModel.updateWatchStatus(WatchType.fromInternalId(this.itemId))
            }
        }

        result_mini_sync?.adapter = ImageAdapter(
            R.layout.result_mini_image,
            nextFocusDown = R.id.result_sync_set_score,
            clickCallback = { action ->
                if (action == IMAGE_CLICK || action == IMAGE_LONG_CLICK) {
                    if (result_overlapping_panels?.getSelectedPanel()?.ordinal == 1) {
                        result_overlapping_panels?.openStartPanel()
                    } else {
                        result_overlapping_panels?.closePanels()
                    }
                }
            })


        observe(viewModel.selectPopup) { popup ->
            when (popup) {
                is Some.Success -> {
                    popupDialog?.dismissSafe(activity)

                    popupDialog = activity?.let { act ->
                        val pop = popup.value
                        val options = pop.getOptions(act)
                        val title = pop.getTitle(act)

                        act.showBottomDialogInstant(
                            options, title, {
                                popupDialog = null
                                pop.callback(null)
                            }, {
                                popupDialog = null
                                pop.callback(it)
                            }
                        )
                    }
                }
                is Some.None -> {
                    popupDialog?.dismissSafe(activity)
                    popupDialog = null
                }
            }

            //showBottomDialogInstant
        }

        observe(viewModel.loadedLinks) { load ->
            when (load) {
                is Some.Success -> {
                    if (loadingDialog?.isShowing != true) {
                        loadingDialog?.dismissSafe(activity)
                        loadingDialog = null
                    }
                    loadingDialog = loadingDialog ?: context?.let { ctx ->
                        val builder =
                            BottomSheetDialog(ctx)
                        builder.setContentView(R.layout.bottom_loading)
                        builder.setOnDismissListener {
                            loadingDialog = null
                            viewModel.cancelLinks()
                        }
                        //builder.setOnCancelListener {
                        //    it?.dismiss()
                        //}
                        builder.setCanceledOnTouchOutside(true)

                        builder.show()

                        builder
                    }
                }
                is Some.None -> {
                    loadingDialog?.dismissSafe(activity)
                    loadingDialog = null
                }
            }
        }
        observe(viewModel.selectedSeason) { text ->
            result_season_button.setText(text)

            // If the season button is visible the result season button will be next focus down
            if (result_season_button?.isVisible == true)
                if (result_resume_parent?.isVisible == true)
                    setFocusUpAndDown(result_resume_series_button, result_season_button)
                else
                    setFocusUpAndDown(result_bookmark_button, result_season_button)
        }

        observe(viewModel.selectedDubStatus) { status ->
            result_dub_select?.setText(status)

            if (result_dub_select?.isVisible == true)
                if (result_season_button?.isVisible != true && result_episode_select?.isVisible != true) {
                    if (result_resume_parent?.isVisible == true)
                        setFocusUpAndDown(result_resume_series_button, result_dub_select)
                    else
                        setFocusUpAndDown(result_bookmark_button, result_dub_select)
                }
        }
        observe(viewModel.selectedRange) { range ->
            result_episode_select.setText(range)

            // If Season button is invisible then the bookmark button next focus is episode select
            if (result_episode_select?.isVisible == true)
                if (result_season_button?.isVisible != true) {
                    if (result_resume_parent?.isVisible == true)
                        setFocusUpAndDown(result_resume_series_button, result_episode_select)
                    else
                        setFocusUpAndDown(result_bookmark_button, result_episode_select)
                }
        }

//        val preferDub = context?.getApiDubstatusSettings()?.all { it == DubStatus.Dubbed } == true

        observe(viewModel.dubSubSelections) { range ->
            result_dub_select.setOnClickListener { view ->
                view?.context?.let { ctx ->
                    view.popupMenuNoIconsAndNoStringRes(range
                        .mapNotNull { (text, status) ->
                            Pair(
                                status.ordinal,
                                text?.asStringNull(ctx) ?: return@mapNotNull null
                            )
                        }) {
                        viewModel.changeDubStatus(DubStatus.values()[itemId])
                    }
                }
            }
        }

        observe(viewModel.rangeSelections) { range ->
            result_episode_select?.setOnClickListener { view ->
                view?.context?.let { ctx ->
                    val names = range
                        .mapNotNull { (text, r) ->
                            r to (text?.asStringNull(ctx) ?: return@mapNotNull null)
                        }

                    view.popupMenuNoIconsAndNoStringRes(names.mapIndexed { index, (_, name) ->
                        index to name
                    }) {
                        viewModel.changeRange(names[itemId].first)
                    }
                }
            }
        }

        observe(viewModel.seasonSelections) { seasonList ->
            result_season_button?.setOnClickListener { view ->
                view?.context?.let { ctx ->
                    val names = seasonList
                        .mapNotNull { (text, r) ->
                            r to (text?.asStringNull(ctx) ?: return@mapNotNull null)
                        }

                    view.popupMenuNoIconsAndNoStringRes(names.mapIndexed { index, (_, name) ->
                        index to name
                    }) {
                        viewModel.changeSeason(names[itemId].first)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        PanelsChildGestureRegionObserver.Provider.get().addGestureRegionsUpdateListener(this)
    }

    override fun onGestureRegionsUpdate(gestureRegions: List<Rect>) {
        result_overlapping_panels?.setChildGestureRegions(gestureRegions)
    }

    override fun setRecommendations(rec: List<SearchResponse>?, validApiName: String?) {
        val isInvalid = rec.isNullOrEmpty()
        result_recommendations?.isGone = isInvalid
        result_recommendations_btt?.isGone = isInvalid
        result_recommendations_btt?.setOnClickListener {
            val nextFocusDown = if (result_overlapping_panels?.getSelectedPanel()?.ordinal == 1) {
                result_overlapping_panels?.openEndPanel()
                R.id.result_recommendations
            } else {
                result_overlapping_panels?.closePanels()
                R.id.result_description
            }

            result_recommendations_btt?.nextFocusDownId = nextFocusDown
            result_search?.nextFocusDownId = nextFocusDown
            result_open_in_browser?.nextFocusDownId = nextFocusDown
            result_share?.nextFocusDownId = nextFocusDown
        }
        result_overlapping_panels?.setEndPanelLockState(if (isInvalid) OverlappingPanelsLayout.LockState.CLOSE else OverlappingPanelsLayout.LockState.UNLOCKED)

        val matchAgainst = validApiName ?: rec?.firstOrNull()?.apiName
        rec?.map { it.apiName }?.distinct()?.let { apiNames ->
            // very dirty selection
            result_recommendations_filter_button?.isVisible = apiNames.size > 1
            result_recommendations_filter_button?.text = matchAgainst
            result_recommendations_filter_button?.setOnClickListener { _ ->
                activity?.showBottomDialog(
                    apiNames,
                    apiNames.indexOf(matchAgainst),
                    getString(R.string.home_change_provider_img_des), false, {}
                ) {
                    setRecommendations(rec, apiNames[it])
                }
            }
        } ?: run {
            result_recommendations_filter_button?.isVisible = false
        }

        result_recommendations?.post {
            rec?.let { list ->
                (result_recommendations?.adapter as SearchAdapter?)?.updateList(list.filter { it.apiName == matchAgainst })
            }
        }
    }
}