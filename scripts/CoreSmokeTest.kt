import com.wulisu.licenseoverlay.core.*

fun main() {
    val sample = "感谢购买\n下载：https://pan.example/12345\n解压密码：123\n激活码：42531563\n使用教程见网盘"
    check(ActivationCodeParser.parse(sample) == ParseResult.Found("42531563"))
    check(ActivationCodeParser.parse("普通聊天，没有卡密") == ParseResult.NotFound)
    check(ActivationCodeParser.parse("激活码：11111111\n激活码：22222222") is ParseResult.Ambiguous)
    check(LicenseCommand("42531563", LicenseAction.ACTIVATE).action.wireName == "activate")
    check(LicenseCommand("42531563", LicenseAction.REFUND_DISABLE).action.wireName == "refund_disable")
    check(LicenseCommand("42531563", LicenseAction.RENEW, 720).renewHours == 720)
    check(LicenseCommand("42531563", LicenseAction.RENEW, 999).renewHours == 999)
    check(LicenseCommand("42531563", LicenseAction.UNBIND).action.wireName == "unbind")
    check(runCatching { LicenseCommand("42531563", LicenseAction.RENEW) }.isFailure)
    check(runCatching { LicenseCommand("42531563", LicenseAction.RENEW, 1000) }.isFailure)
    println("core smoke tests passed")
}
