package com.example.materialdrain.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.materialdrain.network.PixeldrainApiService

class ViewModelFactory(
    private val application: Application,
    private val pixeldrainApiService: PixeldrainApiService
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UploadViewModel::class.java)) {
            return UploadViewModel(application, pixeldrainApiService) as T
        }
        if (modelClass.isAssignableFrom(FileInfoViewModel::class.java)) {
            // Pass application to FileInfoViewModel
            return FileInfoViewModel(application, pixeldrainApiService) as T
        }
        // Add more ViewModels here as needed
        // if (modelClass.isAssignableFrom(AnotherViewModel::class.java)) {
        //     return AnotherViewModel(pixeldrainApiService) as T // Example
        // }
        throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
    }
}
