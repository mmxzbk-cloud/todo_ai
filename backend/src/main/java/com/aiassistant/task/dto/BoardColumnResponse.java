package com.aiassistant.task.dto;

import java.util.List;

import com.aiassistant.task.TaskStatus;

/**
 * 看板的一列。看板固定四列（TODO/DOING/BLOCKED/DONE），空列也返回，
 * 否则列会随数据出现和消失，前端布局跟着跳。
 */
public record BoardColumnResponse(TaskStatus status, List<TaskResponse> tasks) {
}
