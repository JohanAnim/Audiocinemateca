package com.johang.audiocinemateca.presentation.community

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.johang.audiocinemateca.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class AnnouncementsFragment : Fragment() {

    private val viewModel: AnnouncementsViewModel by viewModels()
    private lateinit var adapter: AnnouncementAdapter
    private lateinit var emptyText: TextView

    @Inject
    lateinit var authCatalogRepository: com.johang.audiocinemateca.data.AuthCatalogRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_announcements, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyText = view.findViewById<TextView>(R.id.empty_announcements_text)
        val recyclerView = view.findViewById<RecyclerView>(R.id.announcements_recycler_view)
        val fab = view.findViewById<ExtendedFloatingActionButton>(R.id.fab_create_announcement)
        
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val currentUserId = currentUser?.uid ?: ""

        // INICIALIZACIÓN SEGURA: El adaptador se crea de inmediato
        adapter = AnnouncementAdapter(
            onItemClick = { announcement ->
                // Verificaremos los permisos al momento del clic
                checkAdminAndShowDetails(announcement, currentUserId)
            },
            onReactClick = { announcement ->
                showReactionPicker(announcement)
            }
        )
        recyclerView.adapter = adapter

        // Lógica de permisos para el botón FAB (Crear anuncio)
        lifecycleScope.launch {
            val isSuperAdmin = currentUser?.email?.lowercase() == "gutierrezjohanantonio@gmail.com"
            var isAdminRole = false
            try {
                val userDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(currentUserId).get().await()
                isAdminRole = userDoc.getString("role") == "admin"
            } catch (e: Exception) {
                Log.e("Announcements", "Error checking admin role", e)
            }

            if (isSuperAdmin || isAdminRole) {
                fab.visibility = View.VISIBLE
            }
        }

        fab.setOnClickListener {
            showCreateAnnouncementDialog()
        }

        observeViewModel()
    }

    private fun checkAdminAndShowDetails(announcement: com.johang.audiocinemateca.data.model.Announcement, currentUserId: String) {
        lifecycleScope.launch {
            val isSuperAdmin = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.lowercase() == "gutierrezjohanantonio@gmail.com"
            var isAdminRole = false
            try {
                val userDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(currentUserId).get().await()
                isAdminRole = userDoc.getString("role") == "admin"
            } catch (e: Exception) {}

            showAnnouncementDetailsDialog(announcement, currentUserId, isSuperAdmin || isAdminRole)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.announcements.collect {
                adapter.updateItems(it)
                emptyText.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showAnnouncementDetailsDialog(
        announcement: com.johang.audiocinemateca.data.model.Announcement,
        currentUserId: String,
        isAdmin: Boolean
    ) {
        val reactionCounts = announcement.reactions.values.groupingBy { it }.eachCount()
        val userReaction = announcement.reactions[currentUserId]
        
        val reactionText = if (reactionCounts.isEmpty()) "Sin reacciones aún." 
                          else "Reacciones: " + reactionCounts.entries.joinToString(", ") { "${it.key} ${it.value}" }

        val dialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("${announcement.adminName} (Administrador)")
            .setMessage("${announcement.text}\n\n$reactionText")
            .setPositiveButton("Cerrar", null)

        if (userReaction != null) {
            dialogBuilder.setNeutralButton("Quitar mi reacción ($userReaction)") { _, _ ->
                viewModel.toggleReaction(announcement.id, userReaction)
            }
        } else {
            dialogBuilder.setNeutralButton("Reaccionar") { _, _ ->
                showReactionPicker(announcement)
            }
        }

        if (isAdmin) {
            dialogBuilder.setNegativeButton("Eliminar", null)
        }

        val dialog = dialogBuilder.show()

        if (isAdmin) {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).apply {
                setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                setOnClickListener {
                    showDeleteConfirmation(announcement.id)
                    dialog.dismiss()
                }
            }
        }
    }

    private fun showDeleteConfirmation(id: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Eliminar anuncio?")
            .setMessage("Esta acción es permanente.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sí, eliminar") { _, _ ->
                viewModel.deleteAnnouncement(id)
            }
            .show()
    }

    private fun showCreateAnnouncementDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Escribe el anuncio aquí..."
            setPadding(48, 40, 48, 48)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nuevo Anuncio Oficial")
            .setView(editText)
            .setPositiveButton("Publicar") { _, _ ->
                val text = editText.text.toString()
                if (text.isNotBlank()) {
                    viewModel.postAnnouncement(text)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showReactionPicker(announcement: com.johang.audiocinemateca.data.model.Announcement) {
        val emojis = arrayOf("❤️ Me encanta", "🔥 Genial", "👏 Gracias", "📢 Enterado", "🤔 Interesante")
        val emojiValues = arrayOf("❤️", "🔥", "👏", "📢", "🤔")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reaccionar al anuncio")
            .setItems(emojis) { _, which ->
                viewModel.toggleReaction(announcement.id, emojiValues[which])
            }
            .show()
    }
}