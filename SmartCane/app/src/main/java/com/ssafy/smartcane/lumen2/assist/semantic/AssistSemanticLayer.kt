package com.ssafy.smartcane.lumen2.assist

internal object AssistSemanticLayer {
    fun isWalkable(label: Int): Boolean {
        return label == 5 || label == 6
    }

    fun obstacleKind(label: Int): AssistObstacleKind {
        return if (label == 4) AssistObstacleKind.ROAD else AssistObstacleKind.NON_WALKABLE
    }

    fun displayKind(label: Int): AssistObstacleKind {
        return if (isWalkable(label)) AssistObstacleKind.WALKABLE else obstacleKind(label)
    }
}
