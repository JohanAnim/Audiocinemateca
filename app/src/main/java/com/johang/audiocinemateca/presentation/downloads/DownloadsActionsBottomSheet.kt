package com.johang.audiocinemateca.presentation.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.entities.DownloadEntity

class DownloadsActionsBottomSheet : BottomSheetDialogFragment() {

    interface DownloadsActionsListener {
        fun onDeleteClicked(contentId: String, partIndex: Int, episodeIndex: Int, title: String, isGroup: Boolean)
        fun onViewDetailsClicked(itemId: String, itemType: String)
    }

    private var listener: DownloadsActionsListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_downloads_actions_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = arguments?.getString(ARG_TITLE) ?: "Opciones"
        val contentId = arguments?.getString(ARG_CONTENT_ID) ?: ""
        val partIndex = arguments?.getInt(ARG_PART_INDEX) ?: -1
        val episodeIndex = arguments?.getInt(ARG_EPISODE_INDEX) ?: -1
        val isGroup = arguments?.getBoolean(ARG_IS_GROUP) ?: false
        val contentType = arguments?.getString(ARG_CONTENT_TYPE) ?: ""

        view.findViewById<TextView>(R.id.bottom_sheet_title).text = "Opciones para: $title"

        val viewDetailsButton: Button = view.findViewById(R.id.action_view_details)
        val deleteButton: Button = view.findViewById(R.id.action_delete)

        // Show/hide "Ver detalles" based on whether it's a group or single item
        // Episodes don't have a "Ver detalles" option
        if (isGroup || episodeIndex == -1) {
            viewDetailsButton.visibility = View.VISIBLE
        } else {
            viewDetailsButton.visibility = View.GONE
        }

        viewDetailsButton.setOnClickListener {
            listener?.onViewDetailsClicked(contentId, contentType)
            dismiss()
        }

        deleteButton.setOnClickListener {
            listener?.onDeleteClicked(contentId, partIndex, episodeIndex, title, isGroup)
            dismiss()
        }
    }

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        if (parentFragment is DownloadsActionsListener) {
            listener = parentFragment as DownloadsActionsListener
        } else if (context is DownloadsActionsListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement DownloadsActionsListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        const val TAG = "DownloadsActionsBottomSheet"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_CONTENT_ID = "arg_content_id"
        private const val ARG_PART_INDEX = "arg_part_index"
        private const val ARG_EPISODE_INDEX = "arg_episode_index"
        private const val ARG_IS_GROUP = "arg_is_group"
        private const val ARG_CONTENT_TYPE = "arg_content_type"

        fun newInstance(
            title: String,
            contentId: String,
            partIndex: Int = -1,
            episodeIndex: Int = -1,
            isGroup: Boolean = false,
            contentType: String = ""
        ): DownloadsActionsBottomSheet {
            val fragment = DownloadsActionsBottomSheet()
            val args = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_CONTENT_ID, contentId)
                putInt(ARG_PART_INDEX, partIndex)
                putInt(ARG_EPISODE_INDEX, episodeIndex)
                putBoolean(ARG_IS_GROUP, isGroup)
                putString(ARG_CONTENT_TYPE, contentType)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
