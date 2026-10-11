package hu.atelierpad.core

import java.io.*
import org.junit.Assert.*
import org.junit.Test

class CoreJUnitTest {
    @Test fun fullOfflineRegressionSuite() {
        val count=hu.atelierpad.tests.runCoreRegression {name->
            requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {"Missing fixture: $name"}.use {it.readBytes()}
        }
        assertTrue(count>=50)
    }
    @Test fun archiveRoundTrip(){val p=Project();val bytes=ByteArrayOutputStream();ProjectArchive.write(bytes,p){error("no assets")};assertEquals(p,ProjectArchive.read(ByteArrayInputStream(bytes.toByteArray())).project)}
    @Test fun pdfFit(){val g=ExportGeometry.pdf(1600,1200,300,PdfPaper.A4);assertEquals(842,g.pageWidth);assertEquals(595,g.pageHeight);assertEquals(4f/3,g.width/g.height,.001f)}
    @Test fun history(){val p=Project();val h=ProjectHistory(p);h.commit(p.copy(title="Test"),"rename");assertTrue(h.undo());assertEquals(p,h.current);assertTrue(h.redo());assertEquals("Test",h.current.title)}
    @Test fun fill(){val pixels=intArrayOf(0,0,0xff000000.toInt(),0,0,0xff000000.toInt());assertEquals(4,FloodFill.mask(pixels,3,2,0,0,0).count{it})}
}
