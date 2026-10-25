package cl.inacap.estacionamiento

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun usaElPaqueteCorrecto() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("cl.inacap.estacionamiento", appContext.packageName)
    }
}
