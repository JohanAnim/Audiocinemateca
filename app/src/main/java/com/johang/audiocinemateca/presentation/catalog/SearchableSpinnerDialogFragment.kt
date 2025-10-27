package com.johang.audiocinemateca.presentation.catalog

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.johang.audiocinemateca.R

class SearchableSpinnerDialogFragment(
    private val title: String,
    private val items: Array<String>,
    private val currentOption: String?,
    private val listener: (String) -> Unit
) : DialogFragment() {

    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_searchable_spinner, null)
        val searchEditText = view.findViewById<EditText>(R.id.search_edit_text)
        val listView = view.findViewById<ListView>(R.id.list_view)
        val emptyTextView = view.findViewById<TextView>(R.id.empty_text_view)

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_single_choice, items)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        val checkedItem = items.indexOf(currentOption)
        if (checkedItem != -1) {
            listView.setItemChecked(checkedItem, true)
        } else {
            listView.setItemChecked(0, true)
        }
        listView.emptyView = emptyTextView

        listView.setOnItemClickListener { _, _, position, _ ->
            listener(adapter.getItem(position)!!)
            dismiss()
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
                listView.clearChoices()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        return AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(view)
            .create()
    }
}