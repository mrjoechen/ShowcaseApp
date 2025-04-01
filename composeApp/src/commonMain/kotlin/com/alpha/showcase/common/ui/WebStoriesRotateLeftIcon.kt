package com.alpha.showcase.ui

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath

// 缓存旋转后的图标
private var _webStoriesRotatedLeft: ImageVector? = null

val WebStoriesRotateLeft: ImageVector
    get() {
        if (_webStoriesRotatedLeft != null) {
            return _webStoriesRotatedLeft!!
        }
        _webStoriesRotatedLeft = materialIcon(name = "Outlined.WebStoriesRotatedLeft") {
            // 原 Path 1: M17 4v16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2z
            // 变换后:
            materialPath {
                moveTo(4.0f, 7.0f) // M(17,4) -> (4, 24-17) = (4, 7)
                horizontalLineTo(20.0f) // V20 -> H20 (Point: 20, 7)
                curveTo(20.0f, 5.9f, 19.1f, 5.0f, 18.0f, 5.0f) // C(18.1,20, 19,19.1, 19,18) -> C(20, 5.9, 19.1, 5, 18, 5)
                horizontalLineTo(6.0f) // V6 -> H6 (Point: 6, 5)
                curveTo(4.9f, 5.0f, 4.0f, 5.9f, 4.0f, 7.0f) // C(19,4.9, 18.1,4, 17,4) -> C(4.9, 5, 4, 5.9, 4, 7)
                close()
            }
            // 原 Path 2: M13 2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h9c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 18H4V4h9v16z
            // 变换后:
            materialPath {
                moveTo(2.0f, 11.0f) // M(13,2) -> (2, 24-13) = (2, 11)
                verticalLineTo(20.0f)          // H4 -> V20
                curveTo(2.0f, 21.1f, 2.9f, 22.0f, 4.0f, 22.0f) // c(-1.1,0,-2,.9,-2,2) -> C(2,21.1, 2.9,22, 4,22)
                horizontalLineTo(20.0f)       // V20 -> H20
                curveTo(21.1f, 22.0f, 22.0f, 21.1f, 22.0f, 20.0f) // c(0,1.1,.9,2,2,2) -> C(21.1,22, 22,21.1, 22,20)
                verticalLineTo(11.0f)          // H13 -> V11
                curveTo(22.0f, 9.9f, 21.1f, 9.0f, 20.0f, 9.0f) // c(1.1,0,2,-.9,2,-2) -> C(22,9.9, 21.1,9, 20,9)
                horizontalLineTo(4.0f)         // V4 -> H4
                curveTo(2.9f, 9.0f, 2.0f, 9.9f, 2.0f, 11.0f) // c(0,-1.1,-.9,-2,-2,-2) -> C(2.9,9, 2,9.9, 2,11)
                close()
                // Inner rectangle: M13 20 H4 V4 H13 V20 z
                moveTo(20.0f, 11.0f) // M(13, 20) -> (20, 11)
                verticalLineTo(20.0f) // H4 -> V20 (Point: 20, 20)
                horizontalLineTo(4.0f) // V4 -> H4 (Point: 4, 20)
                verticalLineTo(11.0f) // H13 -> V11 (Point: 4, 11)
                horizontalLineTo(20.0f) // V20 -> H20 (Point: 20, 11)
                close()
            }
            // 原 Path 3: M21 6v12c.83 0 1.5-.67 1.5-1.5v-9C22.5 6.67 21.83 6 21 6z
            // 使用相对命令变换: c(dx1,dy1, dx2,dy2, dx,dy) -> c(dy1,-dx1, dy2,-dx2, dy,-dx)
            // 变换后:
            materialPath {
                moveTo(6.0f, 3.0f) // M(21,6) -> (6, 24-21) = (6, 3)
                horizontalLineToRelative(12.0f) // v12 -> h12 (Point: 18, 3)
                curveToRelative(0.0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f) // c(0.83,0, 1.5,-.67, 1.5,-1.5) -> c(0,-.83, -.67,-1.5, -1.5,-1.5) (Point: 16.5, 1.5)
                horizontalLineToRelative(-9.0f) // v(-9) -> h(-9) (Point: 7.5, 1.5)
                curveToRelative(-0.83f, 0.0f, -1.5f, 0.67f, -1.5f, 1.5f) // C(22.5, 6.67, 21.83, 6, 21, 6) is equiv to c(0,-.83,-.67,-1.5,-1.5,-1.5) -> c(-.83,0, -1.5,.67, -1.5,1.5) (Point: 6, 3)
                close()
            }
        }
        return _webStoriesRotatedLeft!!
    }