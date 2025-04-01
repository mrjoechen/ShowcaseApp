package com.alpha.showcase.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath

// 缓存旋转后的图标
private var _viewRow: ImageVector? = null

val ViewRow: ImageVector
    get() {
        if (_viewRow != null) {
            return _viewRow!!
        }
        _viewRow = materialIcon(name = "Outlined.ViewRow") {
            materialPath {
                // 原坐标 (x, y)
                // 逆时针旋转 90 度后新坐标 (围绕 24x24 画布中心 (12, 12))： (y, 24 - x)
                // Hx -> V(24-x)
                // Vy -> Hy

                // 原 Outer Rect: M(3,5) V19 H21 V5 H3 Z
                moveTo(5.0f, 21.0f)      // M(3,5) -> (5, 24-3) = (5, 21)
                horizontalLineTo(19.0f)  // V19 -> H19
                verticalLineTo(3.0f)     // H21 -> V(24-21) = V3
                horizontalLineTo(5.0f)   // V5 -> H5
                verticalLineTo(21.0f)    // H3 -> V(24-3) = V21
                close()

                // 原 Inner Rect 1 (最左): M(8.33, 17) H5 V7 H8.33 V17 Z (等效)
                moveTo(17.0f, 15.67f) // M(8.33, 17) -> (17, 24-8.33) = (17, 15.67)
                verticalLineTo(19.0f)   // H5 -> V(24-5) = V19
                horizontalLineTo(7.0f)    // V7 -> H7
                verticalLineTo(15.67f) // H8.33 -> V(24-8.33) = V15.67
                horizontalLineTo(17.0f)  // V17 -> H17
                // close() // 由 H17 隐式闭合

                // 原 Inner Rect 2 (中间): M(13.67, 17) H10.33 V7 H13.67 V17 Z (等效)
                moveTo(17.0f, 10.33f) // M(13.67, 17) -> (17, 24-13.67) = (17, 10.33)
                verticalLineTo(13.67f) // H10.33 -> V(24-10.33) = V13.67
                horizontalLineTo(7.0f)    // V7 -> H7
                verticalLineTo(10.33f) // H13.67 -> V(24-13.67) = V10.33
                horizontalLineTo(17.0f)  // V17 -> H17
                // close() // 由 H17 隐式闭合

                // 原 Inner Rect 3 (最右): M(19, 17) H15.67 V7 H19 V17 Z (等效)
                moveTo(17.0f, 5.0f)   // M(19, 17) -> (17, 24-19) = (17, 5)
                verticalLineTo(8.33f) // H15.67 -> V(24-15.67) = V8.33
                horizontalLineTo(7.0f)   // V7 -> H7
                verticalLineTo(5.0f)    // H19 -> V(24-19) = V5
                horizontalLineTo(17.0f) // V17 -> H17
                // close() // 由 H17 隐式闭合
            }
        }
        return _viewRow!!
    }

// 注意：原始的 _viewColumn 变量现在不再由此 getter 使用。
// 如果没有其他地方使用它，可以考虑移除。
// private var _viewColumn: ImageVector? = null