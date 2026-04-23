package com.example.temiapp

/**
 * 全域狀態管理：用來確保 Temi 在執行任務時不會被重複下指令
 */
object AppStatus {
    // 預設為不忙碌
    @Volatile
    var isBusy: Boolean = false

    // 記錄目前任務的說明，顯示在手機網頁上給護理師看
    var currentTaskName: String = "空閒"
}