package com.netvision.app.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class SnmpPacketTest {

    @Test
    fun testSnmpV1GetRequestEncoding() {
        val reqId = 1001
        val community = "netvision"
        val oid = SnmpV2Client.OID_SYS_DESCR

        val bytes = SnmpV2Client.buildGetRequest(reqId, community, oid, version = 0)

        assertNotNull(bytes)
        assertTrue("Packet should have length > 20", bytes.size > 20)

        // 1. Top tag should be SEQUENCE (0x30)
        assertEquals(0x30.toByte(), bytes[0])

        // 2. Version tag (0x02), length (0x01), value (0x00 for SNMPv1)
        assertEquals(0x02.toByte(), bytes[2])
        assertEquals(0x01.toByte(), bytes[3])
        assertEquals(0x00.toByte(), bytes[4])

        // 3. Community tag (0x04), length (9 for "netvision")
        assertEquals(0x04.toByte(), bytes[5])
        assertEquals(9.toByte(), bytes[6])
        val commStr = String(bytes, 7, 9, Charsets.US_ASCII)
        assertEquals("netvision", commStr)

        // 4. Verify presence of VarBind SEQUENCE tag (0x30) inside VarBindList (0x30)
        // This was the crucial fix that prevents RouterOS and AirOS from silently dropping packets
        var foundVarBindSequence = false
        for (i in 16 until bytes.size - 4) {
            if (bytes[i] == 0x30.toByte() && bytes[i + 2] == 0x30.toByte() && bytes[i + 4] == 0x06.toByte()) {
                foundVarBindSequence = true
                break
            }
        }
        assertTrue("VarBind SEQUENCE (0x30) must wrap OID (0x06)", foundVarBindSequence)
    }

    @Test
    fun testSnmpV2cGetRequestEncoding() {
        val reqId = 1002
        val community = "netvision"
        val oid = SnmpV2Client.OID_SYS_NAME

        val bytes = SnmpV2Client.buildGetRequest(reqId, community, oid, version = 1)

        assertNotNull(bytes)
        assertEquals(0x30.toByte(), bytes[0])

        // Version for SNMPv2c is INTEGER 1 (0x02, 0x01, 0x01)
        assertEquals(0x02.toByte(), bytes[2])
        assertEquals(0x01.toByte(), bytes[3])
        assertEquals(0x01.toByte(), bytes[4])
    }

    @Test
    fun testSnmpV1ResponseDecoding() {
        val reqId = 2001
        val community = "netvision"
        val responseString = "Linux 2.6.32.71-UBNT AirOS"

        val packet = buildSimulatedResponse(version = 0, reqId = reqId, community = community, stringValue = responseString)
        val parsed = SnmpV2Client.parseSnmpResponse(packet, reqId, community)

        assertTrue("Parsed response should be successful", parsed.isSuccess)
        assertEquals("v1", parsed.versionDecoded)
        assertEquals(responseString, parsed.stringValue)
    }

    @Test
    fun testSnmpV2cResponseDecoding() {
        val reqId = 2002
        val community = "netvision"
        val responseString = "RouterOS RB750Gr3"

        val packet = buildSimulatedResponse(version = 1, reqId = reqId, community = community, stringValue = responseString)
        val parsed = SnmpV2Client.parseSnmpResponse(packet, reqId, community)

        assertTrue("Parsed response should be successful", parsed.isSuccess)
        assertEquals("v2c", parsed.versionDecoded)
        assertEquals(responseString, parsed.stringValue)
    }

    @Test
    fun testRequestIdMismatchRejection() {
        val sentReqId = 3001
        val receivedReqId = 3999
        val packet = buildSimulatedResponse(version = 1, reqId = receivedReqId, community = "netvision", stringValue = "RouterOS")

        val parsed = SnmpV2Client.parseSnmpResponse(packet, sentReqId, "netvision")

        assertFalse("Should fail on request ID mismatch", parsed.isSuccess)
        assertEquals("Request ID Mismatch", parsed.summary)
    }

    @Test
    fun testCommunityMasking() {
        assertEquals("n*******n", SnmpV2Client.maskCommunity("netvision"))
        assertEquals("p****c", SnmpV2Client.maskCommunity("public"))
        assertEquals("**", SnmpV2Client.maskCommunity("ab"))
    }

    @Test
    fun testTimeTicksFormatting() {
        // 100 ticks = 1 second
        // 9000000 ticks = 90000 seconds = 1d 1h 0m
        val formatted = SnmpV2Client.formatTimeTicks(9000000L)
        assertTrue(formatted.contains("d") && formatted.contains("h"))
    }

    @Test
    fun testModelExtraction() {
        val mtxDescr = "RouterOS RB750Gr3"
        val mtxModel = SnmpV2Client.extractModel(mtxDescr)
        assertEquals("MikroTik RB750Gr3", mtxModel)

        val ubntDescr = "Linux 2.6.32.71-UBNT #1 PREEMPT LiteBeam 5AC Gen2"
        val ubntModel = SnmpV2Client.extractModel(ubntDescr)
        assertTrue(ubntModel.contains("LiteBeam 5AC"))
    }

    /**
     * Helper to synthesize a standard ASN.1 BER SNMP response for test assertions
     */
    private fun buildSimulatedResponse(version: Int, reqId: Int, community: String, stringValue: String): ByteArray {
        val strBytes = stringValue.toByteArray(Charsets.UTF_8)
        val oidBytes = SnmpV2Client.OID_SYS_DESCR

        // VarBind
        val vbInner = ByteArrayOutputStream()
        vbInner.write(0x06)
        vbInner.write(oidBytes.size)
        vbInner.write(oidBytes)
        vbInner.write(0x04) // OCTET STRING
        vbInner.write(strBytes.size)
        vbInner.write(strBytes)

        val vbSeq = ByteArrayOutputStream()
        vbSeq.write(0x30)
        vbSeq.write(vbInner.size())
        vbSeq.write(vbInner.toByteArray())

        // VarBindList
        val vbList = ByteArrayOutputStream()
        vbList.write(0x30)
        vbList.write(vbSeq.size())
        vbList.write(vbSeq.toByteArray())

        // PDU 0xA2
        val pdu = ByteArrayOutputStream()
        // reqId (2 bytes)
        pdu.write(byteArrayOf(0x02, 0x02, (reqId shr 8).toByte(), (reqId and 0xFF).toByte()))
        // error-status 0
        pdu.write(byteArrayOf(0x02, 0x01, 0x00))
        // error-index 0
        pdu.write(byteArrayOf(0x02, 0x01, 0x00))
        pdu.write(vbList.toByteArray())

        val pduSeq = ByteArrayOutputStream()
        pduSeq.write(0xA2)
        pduSeq.write(pdu.size())
        pduSeq.write(pdu.toByteArray())

        // Top Message
        val msg = ByteArrayOutputStream()
        msg.write(byteArrayOf(0x02, 0x01, version.toByte()))
        val commBytes = community.toByteArray(Charsets.US_ASCII)
        msg.write(0x04)
        msg.write(commBytes.size)
        msg.write(commBytes)
        msg.write(pduSeq.toByteArray())

        val top = ByteArrayOutputStream()
        top.write(0x30)
        top.write(msg.size())
        top.write(msg.toByteArray())

        return top.toByteArray()
    }
}
