package com.gramavaxi.ui.common

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.gramavaxi.R
import com.gramavaxi.data.model.Animal
import com.gramavaxi.util.DateUtils

fun Context.statusLabel(animal: Animal): String {
    if (animal.isSick) return getString(R.string.status_sick)
    val days = DateUtils.daysUntil(animal.nextShotDate)
    return when {
        days < 0 -> getString(R.string.status_overdue)
        days <= 14 -> getString(R.string.status_due)
        else -> getString(R.string.status_ok)
    }
}

fun Context.statusColor(animal: Animal): Int {
    if (animal.isSick) return ContextCompat.getColor(this, R.color.status_red)
    val days = DateUtils.daysUntil(animal.nextShotDate)
    return when {
        days < 0 -> ContextCompat.getColor(this, R.color.status_red)
        days <= 14 -> ContextCompat.getColor(this, R.color.status_amber)
        else -> ContextCompat.getColor(this, R.color.status_green)
    }
}

fun Context.animalRow(animal: Animal, onDelete: ((Animal) -> Unit)? = null): View {
    val context = this
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24, 18, 24, 18)
        background = ContextCompat.getDrawable(context, R.drawable.bg_card)

        addView(TextView(context).apply {
            text = animal.name
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        })

        addView(TextView(context).apply {
            text = getString(R.string.unique_animal_id_value, animal.uniqueAnimalId)
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.brand_primary))
        })

        addView(TextView(context).apply {
            text = "${animal.type} - ${animal.breed} - ${getString(R.string.next_shot)} ${DateUtils.formatDate(animal.nextShotDate)}"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        })

        addView(TextView(context).apply {
            text = statusLabel(animal)
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(12, 6, 12, 6)
            background = roundedColor(statusColor(animal))
        })

        if (onDelete != null) {
            addView(Button(context).apply {
                text = getString(R.string.delete_animal)
                setOnClickListener { onDelete(animal) }
            })
        }
    }
}

private fun roundedColor(color: Int) = android.graphics.drawable.GradientDrawable().apply {
    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
    cornerRadius = 18f
    setColor(color)
}
