# BeidouGridCodec
# 北斗网格码编解码器

基于《GB/T 39409-2020》国家标准实现的Java北斗网格码编解码工具库，支持2维/3维编解码。

## 特性

- ✅ 符合国家标准 GB/T 39409-2020
- ✅ 支持二维坐标编码/解码
- ✅ 支持三维坐标（含高度）编码/解码  
- ✅ 模块化设计，职责清晰
- ✅ 高性能缓存优化
- ✅ 完善的错误处理
- ✅ 完整的单元测试覆盖

## 架构设计

项目采用模块化设计，将功能拆分为多个职责明确的类：

- **BeiDouGridUtils** - 主工具类，提供公共API接口
- **BeiDouGridConstants** - 常量类，集中管理网格相关常量
- **BeiDouGridEncoder** - 编码器类，负责所有编码逻辑
- **BeiDouGridDecoder** - 解码器类，负责所有解码逻辑

## 快速开始

### Maven 依赖

```xml
<dependency>
   <groupId>io.github.ywx001</groupId>
   <artifactId>beidou-grid-codec</artifactId>
   <version>1.0.01</version>
</dependency>
```

### 使用示例

```java
// 二维编码
GeoPoint point = GeoPoint.builder()
    .latitude(31.2720680)
    .longitude(120.637779)
    .build();
String code2D = BeiDouGridUtils.encode2D(point, 10);

// 二维解码  
GeoPoint decodedPoint = BeiDouGridUtils.decode2D(code2D);

// 三维编码
String code3D = BeiDouGridUtils.encode3D(point, 50, 10);

// 三维解码
Map<String, Object> decoded3D = BeiDouGridUtils.decode3D(code3D);
```

## API 文档

详细的API文档请查看 [Javadoc](target/apidocs/index.html)

## 性能优化

- 编码映射表使用 ConcurrentHashMap 缓存
- 减少对象创建，优化内存使用
- 使用 StringBuilder 优化字符串拼接
- 集中化的参数验证，快速失败机制

## 许可证

[MIT License](LICENSE) - 自由使用、修改、分发，需保留原作者署名。

## 问题反馈

如果在使用过程中发现问题或有改进建议，请通过以下方式联系：

- 在 [Issues](https://github.com/ywx001/BeidouGridCodec/issues) 中提出
- 发送邮件至: van.s.yu@qq.com

## 贡献

欢迎提交 Pull Request 或提出改进建议！

<img width="154" height="202" alt="北斗网格码示例" src="https://github.com/user-attachments/assets/6e33e114-fc50-467a-95c3-9837e2079084" />
