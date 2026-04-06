package com.johang.audiocinemateca.presentation.notifications

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.databinding.FragmentNotificationsBinding
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.fragment.findNavController

@AndroidEntryPoint
class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: NotificationsViewModel by viewModels()
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        adapter = NotificationAdapter(
            onItemLongClick = { notification ->
                showDeleteConfirmationDialog(notification)
            },
            onNavigate = { destination, url ->
                // CENTRALIZACIÓN: Enviamos el comando a MainActivity para que use la misma lógica de las Push
                val intent = android.content.Intent(requireContext(), com.johang.audiocinemateca.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("navigate_to", destination)
                    putExtra("url", url)
                }
                startActivity(intent)
            }
        )
        return binding.root
    }

    private fun showDeleteConfirmationDialog(notification: com.johang.audiocinemateca.data.local.entities.NotificationEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar notificación")
            .setMessage("¿Deseas eliminar esta notificación del historial?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteNotification(notification)
                android.widget.Toast.makeText(requireContext(), "Notificación eliminada", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Notificaciones"

        setupRecyclerView()
        observeViewModel()
        setupMenu()
    }

    private fun setupRecyclerView() {
        binding.rvNotifications.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.notifications.observe(viewLifecycleOwner) { notifications ->
            adapter.submitList(notifications)
            binding.textNoNotifications.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
            binding.rvNotifications.visibility = if (notifications.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear() 
                menuInflater.inflate(R.menu.notifications_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_clear_notifications -> {
                        showClearConfirmationDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showClearConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Limpiar historial")
            .setMessage("¿Estás seguro de que quieres borrar todas las notificaciones? Esta acción no se puede deshacer.")
            .setPositiveButton("Borrar") { _, _ ->
                viewModel.clearNotifications()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
