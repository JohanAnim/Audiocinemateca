package com.johang.audiocinemateca.presentation.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.databinding.FragmentCommentsSheetBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CommentsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCommentsSheetBinding? = null
    private val binding get() = _binding!!

    // ViewModel compartido con el reproductor
    private val viewModel: PlayerViewModel by viewModels({ requireParentFragment() })

    private lateinit var adapter: CommentAdapter

    companion object {
        fun newInstance(showKeyboard: Boolean = false): CommentsBottomSheetFragment {
            val fragment = CommentsBottomSheetFragment()
            val args = Bundle()
            args.putBoolean("show_keyboard", showKeyboard)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCommentsSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Configuración para que ocupe el 100% de la altura
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            
            // Forzar altura completa
            val layoutParams = it.layoutParams
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            it.layoutParams = layoutParams
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Cargar título de bienvenida
        val detailedTitle = viewModel.getDetailedTitle()
        if (detailedTitle.isNotEmpty()) {
            binding.commentsWelcomeTitle.text = "Comentarios de $detailedTitle"
        } else {
            binding.commentsWelcomeTitle.visibility = View.GONE
        }
        
        setupList()
        observeComments()
        setupListeners()
        
        val showKeyboard = arguments?.getBoolean("show_keyboard") ?: false
        if (showKeyboard) {
            binding.newCommentEditText.requestFocus()
            binding.newCommentEditText.postDelayed({
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(binding.newCommentEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 300)
        }

        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun setupList() {
        adapter = CommentAdapter(
            currentUserId = viewModel.getCurrentUserId(),
            onLikeClick = { comment ->
                viewModel.onCommentLikeClicked(comment.id)
            },
            onReplyClick = { userName ->
                val replyText = "@$userName "
                binding.newCommentEditText.setText(replyText)
                binding.newCommentEditText.setSelection(replyText.length)
                binding.newCommentEditText.requestFocus()
                
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(binding.newCommentEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            },
            onMenuClick = { view, comment ->
                showCommentMenu(view, comment)
            }
        )
        binding.commentsRecyclerView.adapter = adapter
    }

    private fun showCommentMenu(view: View, comment: com.johang.audiocinemateca.data.model.Comment) {
        val popup = android.widget.PopupMenu(requireContext(), view)
        popup.menu.add(0, 1, 0, "Copiar texto")
        
        // Solo mostrar eliminar si es el autor, si no, mostrar reportar
        if (viewModel.getCurrentUserId() == comment.userId) {
            popup.menu.add(0, 2, 1, "Eliminar comentario")
        } else {
            popup.menu.add(0, 3, 1, "Reportar comentario")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { // Copiar
                    val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Comentario", comment.commentText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Texto copiado", Toast.LENGTH_SHORT).show()
                    true
                }
                2 -> { // Eliminar
                    showDeleteConfirmation(comment)
                    true
                }
                3 -> { // Reportar
                    showReportConfirmation(comment)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showDeleteConfirmation(comment: com.johang.audiocinemateca.data.model.Comment) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Eliminar comentario")
            .setMessage("¿Estás seguro de que quieres eliminar este comentario? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.onDeleteCommentClicked(comment.id)
                Toast.makeText(requireContext(), "Comentario eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showReportConfirmation(comment: com.johang.audiocinemateca.data.model.Comment) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Reportar comentario")
            .setMessage("¿Consideras que este comentario infringe las normas? Enviaremos un reporte a los administradores para su revisión.")
            .setPositiveButton("Reportar") { _, _ ->
                viewModel.onReportCommentClicked(comment)
                Toast.makeText(requireContext(), "Gracias. Hemos recibido tu reporte.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun observeComments() {
        lifecycleScope.launch {
            viewModel.allComments.collect { comments ->
                adapter.submitList(comments)
                if (comments.isNotEmpty()) {
                    binding.emptyCommentsContainer.visibility = View.GONE
                    binding.commentsRecyclerView.visibility = View.VISIBLE
                } else {
                    binding.emptyCommentsContainer.visibility = View.VISIBLE
                    binding.commentsRecyclerView.visibility = View.GONE
                }
            }
        }
    }

    private fun setupListeners() {
        binding.closeCommentsButton.setOnClickListener { dismiss() }
        
        binding.filterChipGroup.setOnCheckedChangeListener { _, checkedId ->
            val message = when (checkedId) {
                R.id.chip_recent -> {
                    viewModel.setSortOption(SortOption.RECENT)
                    "Lista ordenada de fecha más reciente a más antigua"
                }
                R.id.chip_oldest -> {
                    viewModel.setSortOption(SortOption.OLDEST)
                    "Lista ordenada de fecha más antigua a más reciente"
                }
                R.id.chip_popular -> {
                    viewModel.setSortOption(SortOption.POPULAR)
                    "Lista ordenada por comentarios más populares"
                }
                R.id.chip_unpopular -> {
                    viewModel.setSortOption(SortOption.UNPOPULAR)
                    "Lista ordenada por comentarios menos populares"
                }
                else -> null
            }
            message?.let { msg ->
                binding.root.postDelayed({
                    if (isAdded) {
                        binding.root.announceForAccessibility(msg)
                    }
                }, 500)
            }
        }
        
        binding.sendCommentButton.setOnClickListener {
            val text = binding.newCommentEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.onAddCommentClicked(text)
                binding.newCommentEditText.text?.clear()
                binding.newCommentEditText.clearFocus()
            } else {
                Toast.makeText(requireContext(), "Escribe algo para comentar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
