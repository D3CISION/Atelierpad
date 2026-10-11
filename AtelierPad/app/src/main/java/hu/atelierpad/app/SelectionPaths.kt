package hu.atelierpad.app

import android.graphics.Path
import hu.atelierpad.core.Selection

/** Same even/odd convention as the platform-independent mask used for flood fill. */
fun Selection.outlinePath():Path=Path().also {path->
    points.forEachIndexed {i,p->if(i==0)path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)}
    path.close()
}
fun Selection.clipPath(width:Int,height:Int):Path=outlinePath().also {path->
    path.fillType=Path.FillType.EVEN_ODD
    if(inverted)path.addRect(0f,0f,width.toFloat(),height.toFloat(),Path.Direction.CW)
}
