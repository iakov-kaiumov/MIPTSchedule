package edu.phystech.iag.kaiumov.schedule.ui.activities

import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import edu.phystech.iag.kaiumov.schedule.Keys
import edu.phystech.iag.kaiumov.shedule.R
import edu.phystech.iag.kaiumov.schedule.ScheduleApp
import edu.phystech.iag.kaiumov.shedule.databinding.ActivityEditBinding
import edu.phystech.iag.kaiumov.shedule.databinding.LessonSpinnerItemBinding
import edu.phystech.iag.kaiumov.schedule.model.ScheduleItem
import edu.phystech.iag.kaiumov.schedule.utils.ColorUtil
import edu.phystech.iag.kaiumov.schedule.utils.DataUtils
import edu.phystech.iag.kaiumov.schedule.utils.ThemeHelper
import edu.phystech.iag.kaiumov.schedule.utils.TimeUtils

//import kotlinx.android.synthetic.main.activity_edit.*
//import kotlinx.android.synthetic.main.lesson_spinner_dropdown_item.view.*


class EditActivity : AppCompatActivity() {

    private var action: String? = null
    private var key : String? = null
    private var item: ScheduleItem? = null

    private lateinit var binding: ActivityEditBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set action bar according to type of activity (edit or create)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.startTimeText.onFocusChangeListener = onFocusChangeListener
        binding.endTimeText.onFocusChangeListener = onFocusChangeListener
        // Add error TextWatcher in case of error in any EditText
        for (i in 0 until binding.editLayout.childCount) {
            if (binding.editLayout.getChildAt(i) is TextInputLayout) {
                val editText = (binding.editLayout.getChildAt(i) as TextInputLayout).editText!!
                editText.addTextChangedListener(
                        object : TextWatcher {
                            override fun afterTextChanged(p0: Editable?) {
                                if (shouldShowError(editText)) {
                                    showError(editText)
                                } else {
                                    hideError(editText)
                                }
                            }

                            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
                            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) = Unit
                        }
                )
            }
        }

        // Search adapter
        binding.profText.setAdapter(
                ArrayAdapter(this, R.layout.search_item, DataUtils.loadProfessorsList(this))
        )

        // Lesson spinner
        val lessonAdapter = LessonAdapter(this)
        lessonAdapter.setDropDownViewResource(R.layout.lesson_spinner_dropdown_item)
        lessonAdapter.addAll(resources.getStringArray(R.array.lesson_types_text).toList())
        binding.lessonSpinner.adapter = lessonAdapter

        // Load data
        key = DataUtils.loadMainKey(this)
        // Two variants: 1) Add new lesson; 2) Edit existed one
        action = intent.action
        when (intent.action) {
            Keys.ACTION_NEW -> {
                supportActionBar?.title = getString(R.string.title_new)
                val defaultDay = intent.getIntExtra(Keys.DAY, 0)
                binding.daySpinner.setSelection(defaultDay - 1)
            }
            Keys.ACTION_EDIT -> {
                supportActionBar?.title = getString(R.string.title_edit)
                item = intent.getSerializableExtra(Keys.ITEM) as ScheduleItem
                loadData()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        DataUtils.hideKeyboard(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.edit_menu, menu)
        when (action) {
            Keys.ACTION_NEW -> menu!!.findItem(R.id.delete_button).isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.delete_button -> showConfirmDialog()
            R.id.save_button -> {
                if (hasError())
                    Toast.makeText(this, getString(R.string.error_hint),
                            Toast.LENGTH_SHORT).show()
                else {
                    when (action) {
                        Keys.ACTION_EDIT -> overwriteData()
                        Keys.ACTION_NEW -> saveNewData()
                    }
                    finish()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Shows alert dialog in case of deleting lesson
     */
    private fun showConfirmDialog() {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle(getString(R.string.dialog_title))
        alertDialog.setMessage(getString(R.string.delete_message))
        alertDialog.setPositiveButton(getString(R.string.button_yes)) { dialogInterface: DialogInterface, _: Int ->
            dialogInterface.cancel()
            deleteItem()
            finish()
        }
        alertDialog.setNegativeButton(getString(R.string.button_no)) { dialogInterface: DialogInterface, _: Int ->
            dialogInterface.cancel()
        }
        alertDialog.setCancelable(true)
        alertDialog.create().show()
    }

    /**
     * For each EditText checks if there're any errors
     */
    private fun shouldShowError(view: TextView): Boolean {
        when (view) {
            binding.lessonText -> return view.text.isEmpty()
            // startTimeText -> return compareTime(startTimeText.text.toString(), endTimeText.text.toString()) > 0
            // endTimeText -> return compareTime(startTimeText.text.toString(), endTimeText.text.toString()) > 0
        }
        return false
    }

    private fun showError(view: TextView) {
        val textInputLayout = view.parent.parent as TextInputLayout
        when (view) {
            binding.lessonText -> textInputLayout.error = getString(R.string.error_empty_string)
            binding.startTimeText -> textInputLayout.error = getString(R.string.error_time)
            binding.endTimeText -> textInputLayout.error = getString(R.string.error_time)
        }
    }

    private fun hideError(view: TextView) {
        val textInputLayout = view.parent.parent as TextInputLayout
        textInputLayout.error = null
    }

    /**
     * Iterates over all EditText's and check if there're any mistakes. If yes, shows it.
     */
    private fun hasError(): Boolean {
        var res = false
        for (i in 0 until binding.editLayout.childCount) {
            if (binding.editLayout.getChildAt(i) is TextInputLayout) {
                val editText = (binding.editLayout.getChildAt(i) as TextInputLayout).editText ?: continue
                if (shouldShowError(editText)) {
                    showError(editText)
                    res = true
                }
            }
        }
        return res
    }

    /**
     * When time EditText is in focus it opens time picker.
     */
    private val onFocusChangeListener = View.OnFocusChangeListener { view, focus ->
        if (!focus) return@OnFocusChangeListener
        binding.editLayout.requestFocus()
        val editText = view as EditText
        val t = editText.text.split(":").map { it.toInt() }
        val hour = t[0]
        val minute = t[1]
        val mTimePicker = TimePickerDialog(this,
                TimePickerDialog.OnTimeSetListener { _, selectedHour, selectedMinute ->
                    val text = String.format("%02d", selectedHour) + ":" + String.format("%02d", selectedMinute)
                    editText.setText(text)
                },
                hour, minute, true)
        mTimePicker.show()
    }

    /**
     * Initialize data views
     */
    private fun loadData() {
        binding.lessonText.setText(item?.name)
        binding.profText.setText(item?.prof)
        binding.placeText.setText(item?.place)
        binding.startTimeText.setText(item?.startTime)
        binding.endTimeText.setText(item?.endTime)
        binding.notesText.setText(item?.notes)
        binding.lessonSpinner.setSelection(resources.getStringArray(R.array.lesson_types).indexOf(item?.type))
        binding.daySpinner.setSelection(item!!.day - 1)
    }

    private fun saveNewData() {
        val newItem = ScheduleItem(binding.lessonText.text.toString(),
            binding.profText.text.toString(),
            binding.placeText.text.toString(),
            binding.daySpinner.selectedItemPosition + 1,
                resources.getStringArray(R.array.lesson_types)[binding.lessonSpinner.selectedItemPosition],
            binding.startTimeText.text.toString(),
            binding.endTimeText.text.toString(),
            binding.notesText.text.toString())
        val app = application as ScheduleApp
        val timetable = app.timetable
        val lessons = timetable[key] ?: return
        lessons.add(newItem)
        lessons.sortWith(Comparator { t1, t2 ->
            return@Comparator if (t1.day != t2.day)
                t1.day - t2.day
            else
                TimeUtils.compareTime(t1.startTime, t2.startTime)
        })
        timetable[key!!] = lessons
        app.updateTimeTable(timetable)
    }

    private fun overwriteData() {
        val newItem = ScheduleItem(binding.lessonText.text.toString(),
            binding.profText.text.toString(),
            binding.placeText.text.toString(),
            binding.daySpinner.selectedItemPosition + 1,
                resources.getStringArray(R.array.lesson_types)[binding.lessonSpinner.selectedItemPosition],
            binding.startTimeText.text.toString(),
            binding.endTimeText.text.toString(),
            binding.notesText.text.toString())
        val app = application as ScheduleApp
        val timetable = app.timetable
        val lessons = timetable[key] ?: return
        val pos = lessons.indexOf(item!!)
        lessons[pos] = newItem
        lessons.sortWith(Comparator { t1, t2 ->
            return@Comparator if (t1.day != t2.day)
                t1.day - t2.day
            else
                TimeUtils.compareTime(t1.startTime, t2.startTime)
        })
        timetable[key!!] = lessons
        app.updateTimeTable(timetable)
    }

    private fun deleteItem() {
        val app = application as ScheduleApp
        val timetable = app.timetable
        val lessons = timetable[key!!] ?: return
        lessons.remove(item!!)
        timetable[key!!] = lessons
        app.updateTimeTable(timetable)
    }

    private class LessonAdapter(context: Context) : ArrayAdapter<String>(context, R.layout.lesson_spinner_item) {
        private val keys = context.resources.getStringArray(R.array.lesson_types)

        private fun getColor(context: Context, position: Int): Int {
            val colorId =  if (ThemeHelper.isDark(context))
                ColorUtil.getTextColor(keys[position])
            else
                ColorUtil.getBackgroundColor(keys[position])
            return ContextCompat.getColor(context, colorId)
        }

        private fun getDrawable(position: Int): Drawable? {
            val drawable = AppCompatResources.getDrawable(context, R.drawable.ic_rounded_square) ?:  return null
            drawable.setTint(getColor(context, position))
            return drawable
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = super.getView(position, convertView, parent)
            val binding = LessonSpinnerItemBinding.bind(v)
            binding.textView.text = getItem(position)
            binding.textView.setCompoundDrawablesWithIntrinsicBounds(getDrawable(position), null, null, null)
            return binding.root
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = super.getDropDownView(position, convertView, parent)
            val binding = LessonSpinnerItemBinding.bind(v)
            // Set text
            binding.textView.text = getItem(position)
            // Set up drawable
            binding.textView.setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(position), null, null, null)
            return binding.root
        }
    }
}