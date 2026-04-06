package com.johang.audiocinemateca.presentation.player

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.databinding.FragmentCommentsSheetBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import com.johang.audiocinemateca.data.local.SharedPreferencesManager

@AndroidEntryPoint
class CommentsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCommentsSheetBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    // ViewModel compartido con el reproductor
    private val viewModel: PlayerViewModel by viewModels({ requireParentFragment() })

    private lateinit var adapter: CommentAdapter
    private var lastAttemptedComment: String = ""

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
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            val layoutParams = it.layoutParams
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            it.layoutParams = layoutParams
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val detailedTitle = viewModel.getDetailedTitle()
        if (detailedTitle.isNotEmpty()) {
            binding.commentsWelcomeTitle.text = "Comentarios de $detailedTitle"
        } else {
            binding.commentsWelcomeTitle.visibility = View.GONE
        }
        
        setupList()
        observeViewModel()
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
            onLikeClick = { comment -> viewModel.onCommentLikeClicked(comment.id) },
            onReplyClick = { userName ->
                val replyText = "@$userName "
                binding.newCommentEditText.setText(replyText)
                binding.newCommentEditText.setSelection(replyText.length)
                binding.newCommentEditText.requestFocus()
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(binding.newCommentEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            },
            onMenuClick = { view, comment -> showCommentMenu(view, comment) }
        )
        binding.commentsRecyclerView.adapter = adapter
    }

    private fun showCommentMenu(view: View, comment: com.johang.audiocinemateca.data.model.Comment) {
        val popup = android.widget.PopupMenu(requireContext(), view)
        popup.menu.add(0, 1, 0, "Copiar texto")
        if (viewModel.getCurrentUserId() == comment.userId) {
            popup.menu.add(0, 2, 1, "Eliminar comentario")
        } else {
            popup.menu.add(0, 3, 1, "Reportar comentario")
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Comentario", comment.commentText))
                    Toast.makeText(requireContext(), "Texto copiado", Toast.LENGTH_SHORT).show()
                    true
                }
                2 -> { showDeleteConfirmation(comment); true }
                3 -> { showReportConfirmation(comment); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showDeleteConfirmation(comment: com.johang.audiocinemateca.data.model.Comment) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Eliminar comentario")
            .setMessage("¿Estás seguro?")
            .setPositiveButton("Eliminar") { _, _ -> viewModel.onDeleteCommentClicked(comment.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showReportConfirmation(comment: com.johang.audiocinemateca.data.model.Comment) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Reportar comentario")
            .setMessage("¿Enviar reporte?")
            .setPositiveButton("Reportar") { _, _ -> viewModel.onReportCommentClicked(comment) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun observeViewModel() {
        // Observar Comentarios
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allComments.collect { comments ->
                    adapter.submitList(comments)
                    binding.emptyCommentsContainer.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
                    binding.commentsRecyclerView.visibility = if (comments.isNotEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        // Observar Errores y RESTAURAR texto si falla
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.toastMessage.collect { message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    // Si el error es sobre publicar, devolvemos el texto al cuadro
                    if (binding.newCommentEditText.text.isNullOrEmpty() && lastAttemptedComment.isNotEmpty()) {
                        binding.newCommentEditText.setText(lastAttemptedComment)
                        binding.newCommentEditText.requestFocus()
                        lastAttemptedComment = "" // Limpiar buffer
                    }
                }
            }
        }

        // Éxito Real
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.commentPostedEvent.collect { success ->
                    if (success) {
                        lastAttemptedComment = "" // Éxito total, vaciamos el backup
                        // El cuadro ya se vació al darle click al botón
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.closeCommentsButton.setOnClickListener { dismiss() }
        
        binding.filterChipGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chip_recent -> viewModel.setSortOption(SortOption.RECENT)
                R.id.chip_oldest -> viewModel.setSortOption(SortOption.OLDEST)
                R.id.chip_popular -> viewModel.setSortOption(SortOption.POPULAR)
                R.id.chip_unpopular -> viewModel.setSortOption(SortOption.UNPOPULAR)
            }
        }
        
        binding.sendCommentButton.setOnClickListener {
            val text = binding.newCommentEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val lastDate = sharedPreferencesManager.getString("last_comment_date", "")
                var count = sharedPreferencesManager.getInt("daily_comment_count", 0)

                if (today != lastDate) {
                    count = 0
                    sharedPreferencesManager.saveString("last_comment_date", today)
                }

                if (count >= 5) {
                    Toast.makeText(requireContext(), "Límite diario alcanzado.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                // 1. BACKUP del texto
                lastAttemptedComment = text
                
                // 2. VACIAR cuadro INMEDIATAMENTE para dar sensación de rapidez
                binding.newCommentEditText.text?.clear()
                
                // 3. Ocultar teclado
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.newCommentEditText.windowToken, 0)

                // 4. Enviar
                viewModel.onAddCommentClicked(text)
                
                // 5. Contar intento
                sharedPreferencesManager.saveInt("daily_comment_count", count + 1)
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