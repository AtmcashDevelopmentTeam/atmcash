package brs.at

import brs.Account
import brs.Burst
import brs.DependencyProvider
import brs.crypto.Crypto
import brs.fluxcapacitor.FluxValues
import brs.props.Props
import brs.util.Convert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLogger

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.*

object AtController {
    // TODO remove static dp
    private var dp: DependencyProvider? = null

    private val logger = LoggerFactory.getLogger(AtController::class.java)

    private val debugLogger by lazy { if (dp!!.propertyService.get(Props.ENABLE_AT_DEBUG_LOG)) logger else NOPLogger.NOP_LOGGER }

    private val costOfOneAT: Int
        get() = AtConstants.AT_ID_SIZE + 16

    fun init(dp: DependencyProvider) {
        AtController.dp = dp
    }

    private fun runSteps(state: AtMachineState): Int {
        state.machineState.running = true
        state.machineState.stopped = false
        state.machineState.finished = false
        state.machineState.dead = false
        state.machineState.steps = 0

        val processor = AtMachineProcessor(state, dp!!.propertyService.get(Props.ENABLE_AT_DEBUG_LOG))

        state.setFreeze(false)

        val stepFee = AtConstants.getInstance().stepFee(state.creationBlockHeight)

        var numSteps = getNumSteps(state.apCode.get(state.machineState.pc), state.creationBlockHeight)

        while (state.machineState.steps + numSteps <= AtConstants.getInstance().maxSteps(state.height)) {

            if (state.getgBalance() < stepFee * numSteps) {
                debugLogger.debug("stopped - not enough balance")
                state.setFreeze(true)
                return 3
            }

            state.setgBalance(state.getgBalance()!! - stepFee * numSteps)
            state.machineState.steps += numSteps
            val rc = processor.processOp(false, false)

            if (rc >= 0) {
                if (state.machineState.stopped) {
                    debugLogger.debug("stopped")
                    state.machineState.running = false
                    return 2
                } else if (state.machineState.finished) {
                    debugLogger.debug("finished")
                    state.machineState.running = false
                    return 1
                }
            } else {
                if (rc == -1)
                    debugLogger.debug("error: overflow")
                else if (rc == -2)
                    debugLogger.debug("error: invalid code")
                else
                    debugLogger.debug("unexpected error")

                if (state.machineState.jumps.contains(state.machineState.err)) {
                    state.machineState.pc = state.machineState.err
                } else {
                    state.machineState.dead = true
                    state.machineState.running = false
                    return 0
                }
            }
            numSteps = getNumSteps(state.apCode.get(state.machineState.pc), state.creationBlockHeight)
        }

        return 5
    }

    private fun getNumSteps(op: Byte, height: Int): Int {
        return if (op >= 0x32 && op < 0x38) AtConstants.getInstance().apiStepMultiplier(height).toInt() else 1

    }

    fun resetMachine(state: AtMachineState) {
        state.machineState.reset()
        listCode(state, true, true)
    }

    private fun listCode(state: AtMachineState, disassembly: Boolean, determineJumps: Boolean) {

        val machineProcessor = AtMachineProcessor(state, dp!!.propertyService.get(Props.ENABLE_AT_DEBUG_LOG))

        val opc = state.machineState.pc
        val osteps = state.machineState.steps

        state.apCode.order(ByteOrder.LITTLE_ENDIAN)
        state.apData.order(ByteOrder.LITTLE_ENDIAN)

        state.machineState.pc = 0
        state.machineState.opc = opc

        while (true) {

            val rc = machineProcessor.processOp(disassembly, determineJumps)
            if (rc <= 0) break

            state.machineState.pc += rc
        }

        state.machineState.steps = osteps
        state.machineState.pc = opc
    }

    @Throws(AtException::class)
    fun checkCreationBytes(creation: ByteArray?, height: Int): Int {
        if (creation == null)
            throw AtException("Creation bytes cannot be null")

        val totalPages: Int
        try {
            val b = ByteBuffer.allocate(creation.size)
            b.order(ByteOrder.LITTLE_ENDIAN)

            b.put(creation)
            b.clear()

            val instance = AtConstants.getInstance()

            val version = b.short
            if (version != instance.atVersion(height)) {
                throw AtException(AtError.INCORRECT_VERSION.description)
            }

            // Ignore reserved bytes
            b.short //future: reserved for future needs

            val codePages = b.short
            if (codePages > instance.maxMachineCodePages(height) || codePages < 1) {
                throw AtException(AtError.INCORRECT_CODE_PAGES.description)
            }

            val dataPages = b.short
            if (dataPages > instance.maxMachineDataPages(height) || dataPages < 0) {
                throw AtException(AtError.INCORRECT_DATA_PAGES.description)
            }

            val callStackPages = b.short
            if (callStackPages > instance.maxMachineCallStackPages(height) || callStackPages < 0) {
                throw AtException(AtError.INCORRECT_CALL_PAGES.description)
            }

            val userStackPages = b.short
            if (userStackPages > instance.maxMachineUserStackPages(height) || userStackPages < 0) {
                throw AtException(AtError.INCORRECT_USER_PAGES.description)
            }

            // Ignore the minimum activation amount
            b.long

            val codeLen = getLength(codePages.toInt(), b)
            if (codeLen < 1 || codeLen > codePages * 256) {
                throw AtException(AtError.INCORRECT_CODE_LENGTH.description)
            }
            val code = ByteArray(codeLen)
            b.get(code, 0, codeLen)

            val dataLen = getLength(dataPages.toInt(), b)
            if (dataLen < 0 || dataLen > dataPages * 256) {
                throw AtException(AtError.INCORRECT_DATA_LENGTH.description)
            }
            val data = ByteArray(dataLen)
            b.get(data, 0, dataLen)

            totalPages = codePages.toInt() + dataPages.toInt() + userStackPages.toInt() + callStackPages.toInt()

            if (b.position() != b.capacity()) {
                throw AtException(AtError.INCORRECT_CREATION_TX.description)
            }

            //TODO note: run code in demo mode for checking if is valid

        } catch (e: BufferUnderflowException) {
            throw AtException(AtError.INCORRECT_CREATION_TX.description)
        }

        return totalPages
    }

    @Throws(AtException::class)
    private fun getLength(nPages: Int, buffer: ByteBuffer): Int {
        var codeLen: Int
        if (nPages * 256 < 257) {
            codeLen = buffer.get().toInt()
            if (codeLen < 0)
                codeLen += (java.lang.Byte.MAX_VALUE + 1) * 2
        } else if (nPages * 256 < java.lang.Short.MAX_VALUE + 1) {
            codeLen = buffer.short.toInt()
            if (codeLen < 0)
                codeLen += (java.lang.Short.MAX_VALUE + 1) * 2
        } else if (nPages * 256 <= Integer.MAX_VALUE) {
            codeLen = buffer.int
        } else {
            throw AtException(AtError.INCORRECT_CODE_LENGTH.description)
        }
        return codeLen
    }

    fun getCurrentBlockATs(freePayload: Int, blockHeight: Int): AtBlock {
        val orderedATs = AT.getOrderedATs(dp!!)
        val keys = orderedATs.iterator()

        val processedATs = ArrayList<AT>()

        val costOfOneAT = costOfOneAT
        var payload = 0
        var totalFee: Long = 0
        var totalAmount: Long = 0

        while (payload <= freePayload - costOfOneAT && keys.hasNext()) {
            val id = keys.next()
            val at = AT.getAT(dp!!, id)

            val atAccountBalance = getATAccountBalance(id)
            val atStateBalance = at.getgBalance()!!

            if (at.freezeOnSameBalance() && atAccountBalance - atStateBalance < at.minActivationAmount()) {
                continue
            }

            if (atAccountBalance >= AtConstants.getInstance().stepFee(at.creationBlockHeight) * AtConstants.getInstance().apiStepMultiplier(at.creationBlockHeight)) {
                try {
                    at.setgBalance(atAccountBalance)
                    at.height = blockHeight
                    at.clearTransactions()
                    at.waitForNumberOfBlocks = at.sleepBetween
                    listCode(at, true, true)
                    runSteps(at)

                    var fee = at.machineState.steps * AtConstants.getInstance().stepFee(at.creationBlockHeight)
                    if (at.machineState.dead) {
                        fee += at.getgBalance()!!
                        at.setgBalance(0L)
                    }
                    at.setpBalance(at.getgBalance())

                    val amount = makeTransactions(at)
                    if (!dp!!.fluxCapacitor.getValue(FluxValues.AT_FIX_BLOCK_4, blockHeight)) {
                        totalAmount = amount
                    } else {
                        totalAmount += amount
                    }

                    totalFee += fee
                    AT.addPendingFee(id!!, fee)

                    payload += costOfOneAT

                    processedATs.add(at)
                } catch (e: Exception) {
                    debugLogger.debug("Error handling AT", e)
                }

            }
        }

        val bytesForBlock: ByteArray?

        bytesForBlock = getBlockATBytes(processedATs, payload)

        return AtBlock(totalFee, totalAmount, bytesForBlock)
    }

    @Throws(AtException::class)
    fun validateATs(blockATs: ByteArray?, blockHeight: Int): AtBlock {
        if (blockATs == null) {
            return AtBlock(0, 0, null)
        }

        val ats = getATsFromBlock(blockATs)

        val processedATs = ArrayList<AT>()

        var totalFee: Long = 0
        val digest = Crypto.md5()
        var md5: ByteArray
        var totalAmount: Long = 0

        for ((atIdBuffer, receivedMd5) in ats) {
            val atId = atIdBuffer.array()
            val at = AT.getAT(dp, atId)
            try {
                at.clearTransactions()
                at.height = blockHeight
                at.waitForNumberOfBlocks = at.sleepBetween

                val atAccountBalance = getATAccountBalance(AtApiHelper.getLong(atId))
                if (atAccountBalance < AtConstants.getInstance().stepFee(at.creationBlockHeight) * AtConstants.getInstance().apiStepMultiplier(at.creationBlockHeight)) {
                    throw AtException("AT has insufficient balance to run")
                }

                if (at.freezeOnSameBalance() && atAccountBalance - at.getgBalance()!! < at.minActivationAmount()) {
                    throw AtException("AT should be frozen due to unchanged balance")
                }

                if (at.nextHeight() > blockHeight) {
                    throw AtException("AT not allowed to run again yet")
                }

                at.setgBalance(atAccountBalance)

                listCode(at, true, true)

                runSteps(at)

                var fee = at.machineState.steps * AtConstants.getInstance().stepFee(at.creationBlockHeight)
                if (at.machineState.dead) {
                    fee += at.getgBalance()!!
                    at.setgBalance(0L)
                }
                at.setpBalance(at.getgBalance())

                if (!dp!!.fluxCapacitor.getValue(FluxValues.AT_FIX_BLOCK_4, blockHeight)) {
                    totalAmount = makeTransactions(at)
                } else {
                    totalAmount += makeTransactions(at)
                }

                totalFee += fee
                AT.addPendingFee(atId, fee)

                processedATs.add(at)

                md5 = digest.digest(at.bytes)
                if (!Arrays.equals(md5, receivedMd5)) {
                    throw AtException("Calculated md5 and received md5 are not matching")
                }
            } catch (e: Exception) {
                debugLogger.debug("ATs error", e)
                throw AtException("ATs error. Block rejected", e)
            }

        }

        for (at in processedATs) {
            at.saveState()
        }

        return AtBlock(totalFee, totalAmount, ByteArray(1))
    }

    @Throws(AtException::class)
    private fun getATsFromBlock(blockATs: ByteArray): LinkedHashMap<ByteBuffer, ByteArray> {
        if (blockATs.size > 0 && blockATs.size % costOfOneAT != 0) {
            throw AtException("blockATs must be a multiple of cost of one AT ( $costOfOneAT )")
        }

        val b = ByteBuffer.wrap(blockATs)
        b.order(ByteOrder.LITTLE_ENDIAN)

        val temp = ByteArray(AtConstants.AT_ID_SIZE)

        val ats = LinkedHashMap<ByteBuffer, ByteArray>()

        while (b.position() < b.capacity()) {
            b.get(temp, 0, temp.size)
            val md5 = ByteArray(16)
            b.get(md5, 0, md5.size)
            val atId = ByteBuffer.allocate(AtConstants.AT_ID_SIZE)
            atId.put(temp)
            atId.clear()
            if (ats.containsKey(atId)) {
                throw AtException("AT included in block multiple times")
            }
            ats[atId] = md5
        }

        if (b.position() != b.capacity()) {
            throw AtException("bytebuffer not matching")
        }

        return ats
    }

    private fun getBlockATBytes(processedATs: List<AT>, payload: Int): ByteArray? {
        if (payload <= 0) {
            return null
        }

        val b = ByteBuffer.allocate(payload)
        b.order(ByteOrder.LITTLE_ENDIAN)

        val digest = Crypto.md5()
        for (at in processedATs) {
            b.put(at.id)
            digest.update(at.bytes)
            b.put(digest.digest())
        }

        return b.array()
    }

    //platform based implementations
    //platform based
    @Throws(AtException::class)
    private fun makeTransactions(at: AT): Long {
        var totalAmount: Long = 0
        if (!dp!!.fluxCapacitor.getValue(FluxValues.AT_FIX_BLOCK_4, at.height)) {
            for (tx in at.transactions) {
                if (AT.findPendingTransaction(tx.recipientId)) {
                    throw AtException("Conflicting transaction found")
                }
            }
        }
        for (tx in at.transactions) {
            totalAmount += tx.amount!!
            AT.addPendingTransaction(tx)
            if (logger.isDebugEnabled) {
                logger.debug("Transaction to {}, amount {}", Convert.toUnsignedLong(AtApiHelper.getLong(tx.recipientId)), tx.amount)
            }
        }
        return totalAmount
    }

    //platform based
    private fun getATAccountBalance(id: Long?): Long {
        val atAccount = Account.getAccount(dp, id!!)

        return atAccount?.balanceNQT ?: 0

    }
}
