package com.example.voxara.ui.screens

import com.example.voxara.ui.PageLinks
import com.example.voxara.ui.Routes

/** The pages of the glance pager, in swipe order: Now (the room and Tono), Dose (today or the week), More. */
internal enum class GlancePage { NOW, DOSE, MENU }

/** The two pictures of the Dose page: today's 24 hours, or the rolling week. */
internal enum class DoseView { TODAY, WEEK }

/** A page of the pager and, for Dose, the view it opens on. */
internal data class GlanceTarget(val page: GlancePage, val view: DoseView = DoseView.TODAY) {
    companion object {
        /** Where a route lands: the old Overview and Now routes open Now; Today and Week open Dose on that view; Headphones opens Dose on the week, where its share lives. */
        fun forRoute(route: String?): GlanceTarget = when (PageLinks.routeFor(route)) {
            Routes.TODAY -> GlanceTarget(GlancePage.DOSE, DoseView.TODAY)
            Routes.WEEK, Routes.HEADPHONES -> GlanceTarget(GlancePage.DOSE, DoseView.WEEK)
            Routes.MENU -> GlanceTarget(GlancePage.MENU)
            else -> GlanceTarget(GlancePage.NOW)
        }
    }
}
