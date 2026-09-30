package com.example

sealed interface ExcelImportUiState {
    object Idle : ExcelImportUiState
    data class Analyzing(val message: String = "Đang phân tích tệp Excel...") : ExcelImportUiState
    data class Preview(val plan: ImportPlan) : ExcelImportUiState
    data class Importing(val message: String = "Đang ghi dữ liệu vào hệ thống...") : ExcelImportUiState
    data class Success(val result: ImportExecutionResult) : ExcelImportUiState
    data class Error(val message: String) : ExcelImportUiState
}
