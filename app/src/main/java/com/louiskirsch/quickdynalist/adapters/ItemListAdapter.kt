package com.louiskirsch.quickdynalist.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.louiskirsch.quickdynalist.OnLinkTouchListener
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.isEllipsized
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.item_list_item.view.*
import nl.pvdberg.hashkode.compareFields
import nl.pvdberg.hashkode.hashKode
import java.lang.Exception
import java.util.*


class ItemListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    val itemText = itemView.itemText!!
    val itemNotes = itemView.itemNotes!!
    val itemChildren = itemView.itemChildren!!
    val itemImage = itemView.itemImage!!
    val itemDetailsButton = itemView.itemDetailsButton!!
}

class DropOffViewHolder(val textView: TextView): RecyclerView.ViewHolder(textView)

class CachedDynalistItem(val item: DynalistItem, context: Context) {
    val spannableText = item.getSpannableText(context).run {
        if (isBlank() && item.image != null)
            SpannableString(context.getString(R.string.placeholder_image))
        else
            this
    }
    val spannableNotes = item.getSpannableNotes(context)
    val spannableChildren = item.getSpannableChildren(context, 5)

    override fun equals(other: Any?) = compareFields(other) {
        one.spannableText.toString() correspondsTo two.spannableText.toString()
        one.spannableChildren.toString() correspondsTo two.spannableChildren.toString()
        one.spannableNotes.toString() correspondsTo two.spannableNotes.toString()
        one.item correspondsTo two.item
    }

    override fun hashCode() = hashKode(spannableText, spannableNotes, spannableChildren, item)
}

class ItemListAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>(), ItemTouchCallback.ItemTouchHelperContract {

    private val items = ArrayList<CachedDynalistItem>()
    var moveInProgress: Boolean = false
        private set

    var onClickListener: ((DynalistItem) -> Unit)? = null
    var onLongClickListener: ((DynalistItem) -> Boolean)? = null
    var onDetailsClickListener: ((DynalistItem) -> Unit)? = null
    var onRowMovedListener: ((DynalistItem, Int) -> Unit)? = null
    var onRowMovedOnDropoff: ((DynalistItem, Int) -> Unit)? = null
    var onRowMovedIntoListener: ((DynalistItem, DynalistItem) -> Unit)? = null
    var onRowSwipedListener: ((DynalistItem) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    fun updateItems(newItems: List<CachedDynalistItem>) {
        if (moveInProgress) {
            return
        }
        val update = {
            items.clear()
            items.addAll(newItems)
            Unit
        }
        val oldSize = items.size
        if (newItems.take(oldSize) == items) {
            val newCount = newItems.size - oldSize
            update()
            notifyItemRangeInserted(oldSize, newCount)
        } else {
            update()
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int {
        if (moveInProgress)
            return items.size + 2
        return items.size
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == R.id.view_type_item) {
            ItemListViewHolder(inflater.inflate(R.layout.item_list_item,
                    parent, false)).apply {
                itemText.setOnTouchListener(OnLinkTouchListener())
                itemNotes.setOnTouchListener(OnLinkTouchListener())
                itemChildren.setOnTouchListener(OnLinkTouchListener())
            }
        } else {
            DropOffViewHolder(inflater.inflate(R.layout.item_list_dropoff,
                    parent, false) as TextView)
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (moveInProgress && (position == 0 || position == itemCount - 1))
            return R.id.view_type_dropoff
        return R.id.view_type_item
    }

    private fun getItemId(item: CachedDynalistItem): Long = item.item.clientId
    override fun getItemId(position: Int): Long {
        return correctPositionForDropOffs(position)?.let { getItemId(items[it]) } ?: run {
            return if (position == 0)
                R.id.dropoff_parent.toLong()
            else
                R.id.dropoff_duplicate.toLong()
        }
    }

    private fun correctPositionForDropOffs(position: Int): Int? {
        if (!moveInProgress)
            return position
        if (getItemViewType(position) == R.id.view_type_dropoff)
            return null
        return position - 1
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == R.id.view_type_item) {
            val fixedPosition = correctPositionForDropOffs(position)!!
            onBindItemViewHolder(holder as ItemListViewHolder, fixedPosition)
        } else {
            onBindDropOffViewHolder(holder as DropOffViewHolder, position)
        }
    }

    private fun onBindDropOffViewHolder(holder: DropOffViewHolder, position: Int) {
        val context = holder.itemView.context
        val text = when (getItemId(position)) {
            R.id.dropoff_parent.toLong() -> context.getString(R.string.dropoff_parent)
            R.id.dropoff_duplicate.toLong() -> context.getString(R.string.dropoff_duplicate)
            else -> throw IllegalStateException("Invalid drop off point")
        }
        holder.textView.text = text
    }

    private fun onBindItemViewHolder(holder: ItemListViewHolder, position: Int) {
        val item = items[position]
        holder.itemText.text = item.spannableText
        holder.itemNotes.visibility = if (item.spannableNotes.isEmpty()) View.GONE else View.VISIBLE
        holder.itemNotes.text = item.spannableNotes
        holder.itemChildren.visibility = if (item.spannableChildren.isEmpty()) View.GONE else View.VISIBLE
        holder.itemChildren.text = item.spannableChildren
        holder.itemView.setOnClickListener { onClickListener?.invoke(items[position].item) }

        holder.itemDetailsButton.visibility = View.GONE
        holder.itemDetailsButton.setOnClickListener {
            onDetailsClickListener?.invoke(items[position].item)
        }
        holder.itemImage.visibility = View.GONE

        val image = item.item.image
        if (image != null) {
            Picasso.get().load(image).into(holder.itemImage, object: Callback {
                override fun onError(e: Exception?) {
                    showDetailButtonIfEllipsized(holder)
                }
                override fun onSuccess() {
                    holder.itemImage.visibility = View.VISIBLE
                    holder.itemImage.setOnClickListener {
                        onDetailsClickListener?.invoke(items[position].item)
                    }
                }
            })
        } else {
            showDetailButtonIfEllipsized(holder)
        }
    }

    private fun showDetailButtonIfEllipsized(holder: ItemListViewHolder) {
        holder.itemNotes.isEllipsized { ellipsized ->
            if (ellipsized) {
                holder.itemDetailsButton.visibility = View.VISIBLE
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        val itemsFromPosition = correctPositionForDropOffs(fromPosition)!!
        val itemsToPosition = correctPositionForDropOffs(toPosition)!!
        if (itemsFromPosition < itemsToPosition) {
            for (i in itemsFromPosition until itemsToPosition) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in itemsFromPosition downTo itemsToPosition + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowMovedToDestination(toPosition: Int) {
        val index = correctPositionForDropOffs(toPosition)!!
        val item = items[index].item
        onRowMovedListener?.invoke(item, index)
    }

    override fun onRowSwiped(position: Int) {
        val item = items[position].item
        items.removeAt(position)
        notifyItemRemoved(position)
        onRowSwipedListener?.invoke(item)
    }

    override fun onMoveStart(position: Int) {
        moveInProgress = true
        notifyItemInserted(0)
        notifyItemInserted(itemCount - 1)
    }

    override fun onMoveEnd(position: Int) {
        notifyItemRemoved(0)
        notifyItemRemoved(itemCount - 1)
        moveInProgress = false
    }

    override fun onRowMovedInto(fromPosition: Int, intoPosition: Int) {
        val fromIndex = correctPositionForDropOffs(fromPosition)!!
        val fromItem = items[fromIndex].item
        if (getItemViewType(intoPosition) == R.id.view_type_dropoff) {
            val itemId = getItemId(intoPosition).toInt()
            if (itemId != R.id.dropoff_duplicate) {
                items.removeAt(fromIndex)
                notifyItemRemoved(fromPosition)
            }
            onRowMovedOnDropoff?.invoke(fromItem, itemId)
        } else {
            val intoIndex = correctPositionForDropOffs(intoPosition)!!
            val intoItem = items[intoIndex].item
            items.removeAt(fromIndex)
            notifyItemRemoved(fromPosition)
            onRowMovedIntoListener?.invoke(fromItem, intoItem)
        }
    }

    override fun canDropOver(position: Int): Boolean {
        return position in 1..(itemCount - 2)
    }

    override fun onLongClick(position: Int) {
        val index = correctPositionForDropOffs(position)!!
        onLongClickListener?.invoke(items[index].item)
    }
}
