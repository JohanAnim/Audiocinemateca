package com.johang.audiocinemateca.presentation.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.local.dao.NotificationDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationDao: NotificationDao
) : ViewModel() {

    val notifications = notificationDao.getAllNotifications().asLiveData()

    fun clearNotifications() {
        viewModelScope.launch {
            notificationDao.clearAll()
        }
    }

    fun markAsRead(id: Int) {
        viewModelScope.launch {
            notificationDao.markAsRead(id)
        }
    }

    fun deleteNotification(notification: com.johang.audiocinemateca.data.local.entities.NotificationEntity) {
        viewModelScope.launch {
            notificationDao.delete(notification)
        }
    }
}
