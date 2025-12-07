# API接口规格文档 (API-SPEC)

## 1. 接口概述

### 1.1 API基础信息
- **基础URL**: `https://localhost:8443/tongrenlu`
- **协议**: HTTPS
- **认证方式**: Session/Cookie
- **数据格式**: JSON
- **编码**: UTF-8

### 1.2 通用约定

#### 请求格式
- GET请求：查询参数放在URL中
- POST/PUT/DELETE请求：数据放在请求体中(JSON格式)

#### 响应格式
```json
{
    "code": 200,
    "message": "success",
    "data": {},
    "timestamp": "2025-12-07T10:30:00Z"
}
```

#### 状态码定义
- `200`: 成功
- `400`: 请求参数错误
- `401`: 未授权
- `403`: 权限不足
- `404`: 资源不存在
- `500`: 服务器内部错误

## 2. 音乐相关接口

### 2.1 获取音乐列表
**接口路径**: `GET /api/music`

**功能描述**: 获取音乐专辑列表，支持分页和搜索

**请求参数**:
```javascript
{
    "page": 1,           // 页码(可选，默认1)
    "size": 20,          // 每页数量(可选，默认20)
    "keyword": "",       // 搜索关键词(可选)
    "artist": "",       // 艺术家筛选(可选)
    "sort": "publishDate" // 排序字段(可选): publishDate, title, accessCount
}
```

**响应示例**:
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "total": 100,
        "page": 1,
        "size": 20,
        "items": [
            {
                "id": 3001,
                "title": "Brilliant Story",
                "artist": "Amateras Records",
                "publishDate": "2016-12-29T00:00:00Z",
                "coverUrl": "https://p2.music.126.net/QQILFj3wjgLt_OA04mbFNA==/109951169299675891.jpg",
                "accessCount": 150
            }
        ]
    }
}
```

### 2.2 获取音乐详情
**接口路径**: `GET /api/music/{id}`

**功能描述**: 根据ID获取音乐专辑详情

**路径参数**:
- `id`: 音乐专辑ID

**响应示例**:
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "id": 3001,
        "title": "Brilliant Story",
        "artist": "Amateras Records",
        "description": "",
        "publishDate": "2016-12-29T00:00:00Z",
        "coverUrl": "https://p2.music.126.net/QQILFj3wjgLt_OA04mbFNA==/109951169299675891.jpg",
        "accessCount": 150,
        "tracks": [
            {
                "id": 10001,
                "title": "Opening Theme",
                "duration": 180,
                "audioUrl": "/music/track/10001"
            }
        ],
        "tags": ["东方Project", "同人音乐", "器乐"]
    }
}
```

### 2.3 音乐播放统计
**接口路径**: `POST /api/music/{id}/play`

**功能描述**: 统计音乐播放次数

**路径参数**:
- `id`: 音乐专辑ID

**请求参数**: 无

**响应示例**:
```json
{
    "code": 200,
    "message": "播放统计成功"
}
```

## 3. 文章相关接口

### 3.1 获取文章列表
**接口路径**: `GET /api/article`

**功能描述**: 获取文章列表，支持分页和标签筛选

**请求参数**:
```javascript
{
    "page": 1,
    "size": 20,
    "tagId": 123,       // 标签ID(可选)
    "authorId": 456,    // 作者ID(可选)
    "sort": "publishDate"
}
```

**响应示例**:
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "total": 50,
        "page": 1,
        "size": 20,
        "items": [
            {
                "id": 2001,
                "title": "东方Project同人创作指南",
                "author": {
                    "id": 456,
                    "name": "创作者A"
                },
                "summary": "东方Project同人创作的经验分享...",
                "publishDate": "2025-11-15T10:00:00Z",
                "viewCount": 250,
                "likeCount": 15,
                "commentCount": 8
            }
        ]
    }
}
```

### 3.2 创建文章
**接口路径**: `POST /api/article`

**功能描述**: 创建新的文章

**请求参数**:
```javascript
{
    "title": "文章标题",
    "content": "文章内容",
    "tags": [123, 456, 789],           // 标签ID列表
    "isPublic": true,                 // 是否公开
    "publishDate": "2025-12-07T10:00:00Z" // 发布时间
}
```

**响应示例**:
```json
{
    "code": 200,
    "message": "文章创建成功",
    "data": {
        "id": 2002,
        "title": "文章标题",
        "status": "published"
    }
}
```

## 4. 标签相关接口

### 4.1 获取标签列表
**接口路径**: `GET /api/tag`

**功能描述**: 获取所有标签，支持层级结构

**请求参数**:
```javascript
{
    "parentId": null,   // 父标签ID(可选)
    "type": "music"     // 标签类型: music, article
}
```

**响应示例**:
```json
{
    "code": 200,
    "message": "success",
    "data": [
        {
            "id": 1,
            "name": "音乐",
            "type": "music",
            "parentId": null,
            "childTags": [
                {
                    "id": 2,
                    "name": "东方Project",
                    "usageCount": 500
                }
            ]
        }
    ]
}
```

### 4.2 标签关联管理
**接口路径**: `POST /api/tag/{tagId}/associate`

**功能描述**: 将标签与内容关联

**请求参数**:
```javascript
{
    "contentType": "music",  // 内容类型: music, article
    "contentId": 3001        // 内容ID
}
```

## 5. 用户相关接口

### 5.1 用户登录
**接口路径**: `POST /api/user/login`

**功能描述**: 用户登录认证

**请求参数**:
```javascript
{
    "username": "user@example.com",
    "password": "password123"
}
```

**响应示例**:
```json
{
    "code": 200,
    "message": "登录成功",
    "data": {
        "userId": 123,
        "username": "user@example.com",
        "displayName": "用户昵称",
        "avatar": "https://example.com/avatar.jpg",
        "token": "jwt_token_here"
    }
}
```

### 5.2 用户收藏
**接口路径**: `POST /api/user/favorite`

**功能描述**: 收藏或取消收藏内容

**请求参数**:
```javascript
{
    "contentType": "music",  // music, article
    "contentId": 3001,
    "action": "add"          // add, remove
}
```

## 6. 管理后台接口

### 6.1 艺术家管理
**接口路径**: `GET /api/admin/artist`

**功能描述**: 管理员获取艺术家列表

**权限要求**: 管理员权限

**请求参数**:
```javascript
{
    "page": 1,
    "size": 50,
    "keyword": ""
}
```

### 6.2 未发布内容管理
**接口路径**: `GET /api/admin/unpublished`

**功能描述**: 查看待审核的未发布内容

**权限要求**: 管理员权限

## 7. 文件上传接口

### 7.1 上传图片
**接口路径**: `POST /api/upload/image`

**功能描述**: 上传图片文件

**Content-Type**: `multipart/form-data`

**请求参数**:
- `file`: 图片文件
- `type`: 图片类型(album, avatar, article)

**响应示例**:
```json
{
    "code": 200,
    "message": "上传成功",
    "data": {
        "url": "/uploads/images/2025/12/07/abc123.jpg",
        "fileId": "file123"
    }
}
```

## 8. 数据统计接口

### 8.1 平台统计
**接口路径**: `GET /api/stats/platform`

**功能描述**: 获取平台总体统计数据

**响应示例**:
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "userCount": 1500,
        "musicCount": 300,
        "articleCount": 500,
        "dailyActiveUsers": 120,
        "weeklyGrowth": 15.5
    }
}
```

## 9. 错误处理规范

### 9.1 通用错误格式
```json
{
    "code": 400,
    "message": "参数验证失败",
    "details": [
        {
            "field": "title",
            "message": "标题不能为空"
        }
    ]
}
```

### 9.2 常见错误码
- `1001`: 用户未登录
- `1002`: 权限不足
- `1003`: 资源不存在
- `1004`: 参数格式错误
- `1005`: 操作失败

## 10. API版本管理

### 10.1 版本策略
- 通过URL路径区分版本: `/api/v1/music`
- 保持向后兼容性
- 废弃的API提供迁移路径

### 10.2 弃用通知
在响应头中标记即将废弃的API：
```
Deprecation: true
Sunset: Mon, 1 Jan 2026 00:00:00 GMT
```

---
**文档版本**: v1.0
**最后更新**: 2025-12-07
**负责人**: 开发团队