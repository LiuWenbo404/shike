package io.liuwenbo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("stall")
public class Stall {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer avgPrepareSec;
    private Integer openStatus;
}