using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class VendorPartitionCalibrationReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "vendor-partition-calibration-reference",
        Title = "32. 极米私有校准分区与出厂矩阵（xgimiconfig / xgimidatabase）",
        Group = "设备接入",
        Summary = "实测探查极米光机马达参数、色温标定矩阵与私有持久化存储挂载点。",
        Sections =
        [
            new ContentSection
            {
                Heading = "极米专属硬件分区挂载点实测",
                Text = "查看极米存放机型出厂几何校准、测距镜头与色轮标定数据的独立块设备：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "检索极米私有分区挂载信息",
                        Code = "mount | grep xgimi",
                        ExpectedOutput =
                            "/dev/block/mmcblk0p50 on /mnt/vendor/xgimiconfig type ext4 (rw,seclabel,nosuid,nodev,noatime,data=ordered)   # 极米硬件出厂配置文件分区\n" +
                            "/dev/block/mmcblk0p51 on /mnt/vendor/xgimidatabase type ext4 (rw,seclabel,nosuid,nodev,noatime,data=ordered) # 极米运行期校准数据库分区"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "出厂型号硬件参数目录结构",
                Text = "实测 `/mnt/vendor/xgimiconfig/` 目录存放各产品硬件线校准矩阵：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看光机型号配置目录",
                        Code = "ls -la /mnt/vendor/xgimiconfig/",
                        ExpectedOutput =
                            "drwxr-xr-x G0073   # 极米特定型号光机马达与对焦曲线\n" +
                            "drwxr-xr-x G0094   # Z6X 系列专用硬件校准数据\n" +
                            "drwxr-xr-x public  # 公共传感器与色温基准表"
                    }
                ],
                BulletPoints =
                [
                    "高危警示：mmcblk0p50/p51 存放出厂激光与梯形校正基准，严禁格式化或随意修改，否则会导致光机无法点亮或对焦失效。"
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`/mnt/vendor/xgimiconfig`：权限为 `root:root 755`，SSH（UID 10068）与 ADB（UID 2000）均可只读遍历目录，但均无写权限（仅 root 可写）。"
                ]
            }
        ]
    };
}
