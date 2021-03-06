package com.louiskirsch.quickdynalist

import android.net.Uri
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.objectbox.DynalistItemFilter

class AuthenticatedEvent(val success: Boolean)
class ItemEvent(val success: Boolean, val retrying: Boolean = false)
class InboxEvent(val configured: Boolean)
class RateLimitDelay(val delay: Long, val jobTag: String)
class DynalistLocateEvent(val item: DynalistItem)
class DynalistFilterEvent(val filter: DynalistItemFilter)
class ForbiddenImageEvent(val uri: Uri)
class ItemAddedEvent(val item: DynalistItem)

enum class SyncStatus { RUNNING, NOT_RUNNING, SUCCESS, NO_SUCCESS }
class SyncEvent(val status: SyncStatus, val isManual: Boolean)
class SyncProgressEvent(val progress: Float)

class NightModeChangedEvent
