package com.johang.audiocinemateca.presentation.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.johang.audiocinemateca.data.model.ChatMessage
import com.johang.audiocinemateca.databinding.FragmentChatMessageOptionsBinding

class ChatMessageOptionsBottomSheet(
    private val message: ChatMessage,
    private val currentUserId: String?,
    private val onAction: (Action) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: FragmentChatMessageOptionsBinding? = null
    private val binding get() = _binding!!

    sealed class Action {
        data class React(val emoji: String) : Action()
        object Reply : Action()
        object Mention : Action()
        object Edit : Action()
        object Delete : Action()
        object Report : Action()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatMessageOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.tvOptionsHeader.text = "Opciones para ${message.senderName}"

        // Configurar carrusel de reacciones
        val emojis = listOf("❤️", "😂", "😮", "😢", "😡", "👍", "🙏", "🔥")
        binding.rvReactionsCarousel.adapter = ReactionsAdapter(emojis) { emoji ->
            onAction(Action.React(emoji))
            dismiss()
        }

        // Mostrar Editar/Eliminar solo si el mensaje es del usuario actual
        val isMe = currentUserId != null && message.senderId == currentUserId
        binding.btnActionEdit.visibility = if (isMe) View.VISIBLE else View.GONE
        binding.btnActionDelete.visibility = if (isMe) View.VISIBLE else View.GONE
        binding.btnActionReport.visibility = if (!isMe) View.VISIBLE else View.GONE

        // Listeners
        binding.btnActionReply.setOnClickListener { onAction(Action.Reply); dismiss() }
        binding.btnActionMention.setOnClickListener { onAction(Action.Mention); dismiss() }
        binding.btnActionEdit.setOnClickListener { onAction(Action.Edit); dismiss() }
        binding.btnActionDelete.setOnClickListener { onAction(Action.Delete); dismiss() }
        binding.btnActionReport.setOnClickListener { onAction(Action.Report); dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
