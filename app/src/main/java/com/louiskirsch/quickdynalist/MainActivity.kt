package com.louiskirsch.quickdynalist

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import com.louiskirsch.quickdynalist.jobs.Bookmark
import com.louiskirsch.quickdynalist.jobs.BookmarksJob
import kotlinx.android.synthetic.main.activity_main.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.*
import java.util.*
import android.util.Pair as UtilPair

class MainActivity : Activity() {
    private val dynalist: Dynalist = Dynalist(this)
    private lateinit var adapter: ArrayAdapter<Bookmark>

    private val preferences: SharedPreferences
        get() = getSharedPreferences("MAIN_ACTIVITY", Context.MODE_PRIVATE)

    private var bookmarkHintCounter: Int
        get() = preferences.getInt("BOOKMARK_HINT_COUNTER", 0)
        set(value) = preferences.edit().putInt("BOOKMARK_HINT_COUNTER", value).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar.inflateMenu(R.menu.quick_dialog_menu)

        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.open_item_list -> {
                    val intent = Intent(this, ItemListActivity::class.java)
                    val selectedBookmark = itemLocation.selectedItem as Bookmark
                    intent.putExtra(ItemListActivity.EXTRA_BOOKMARK, selectedBookmark)
                    intent.putExtra(Intent.EXTRA_TEXT, itemContents.text)
                    val transition = ActivityOptions.makeSceneTransitionAnimation(this,
                            UtilPair.create(toolbar as View, "toolbar"),
                            UtilPair.create(itemContents as View, "itemContents"))
                    startActivity(intent, transition.toBundle())
                    itemContents.text.clear()
                }
                R.id.open_large -> {
                    val intent = Intent(this, AdvancedItemActivity::class.java)
                    intent.putExtra(Intent.EXTRA_SUBJECT, itemLocation.selectedItemPosition)
                    intent.putExtra(Intent.EXTRA_TEXT, itemContents.text)
                    val transition = ActivityOptions.makeSceneTransitionAnimation(this,
                            UtilPair.create(toolbar as View, "toolbar"),
                            UtilPair.create(itemLocation as View, "itemLocation"),
                            UtilPair.create(itemContents as View, "itemContents"))
                    startActivity(intent, transition.toBundle())
                    itemContents.text.clear()
                }
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }

        setupItemContentsTextField()

        submitButton!!.setOnClickListener {
            dynalist.addItem(itemContents!!.text.toString(),
                    itemLocation!!.selectedItem as Bookmark)
            itemContents!!.text.clear()
        }

        itemLocation.setOnTouchListener { _, e ->
            if ((e.action == MotionEvent.ACTION_DOWN ||
                    e.action == MotionEvent.ACTION_POINTER_DOWN) && bookmarkHintCounter < 5) {
                longToast(R.string.bookmark_hint)
                bookmarkHintCounter += 1
            }
            false
        }

        adapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item, dynalist.bookmarks.toMutableList())
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        itemLocation!!.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        dynalist.subscribe()
    }

    private fun setupItemContentsTextField() {
        with(itemContents!!) {
            setupGrowingMultiline(5)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

                override fun afterTextChanged(editable: Editable) {
                    updateSubmitEnabled()
                }
            })
            setOnEditorActionListener { _, actionId, _ ->
                val isDone = actionId == EditorInfo.IME_ACTION_DONE
                if (isDone && submitButton.isEnabled) {
                    submitButton!!.performClick()
                }
                isDone
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateSubmitEnabled()
        if (!dynalist.isAuthenticated && !dynalist.isAuthenticating) {
            dynalist.authenticate()
        } else {
            itemContents!!.requestFocus()
        }
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        dynalist.unsubscribe()
    }

    private fun updateSubmitEnabled() {
        val enabled = dynalist.isAuthenticated && !itemContents!!.text.toString().isEmpty()
        submitButton!!.isEnabled = enabled
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBookmarksUpdated(event: BookmarksUpdatedEvent) {
        adapter.clear()
        adapter.addAll(event.newBookmarks.toList())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAuthenticationEvent(event: AuthenticatedEvent) {
        updateSubmitEnabled()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onItemEvent(event: ItemEvent) {
        if (!event.success)
            toast(R.string.add_item_error)
    }
}
