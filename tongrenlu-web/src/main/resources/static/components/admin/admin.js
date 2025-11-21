$(document).ready(function () {
    let currentPage = 1
    const pageSize = 20
    let currentAlbumId = null
    let currentAlbumTitle = null
    let currentAlbumArtist = null
    let currentAlbumPublishDate = null

    function loadUnpublishedAlbums() {
        const $loading = $('#loading')
        const $list = $('#album-list')
        const $pagination = $('#pagination')

        $loading.show()
        $list.hide()
        $pagination.hide()

        fetch(`/api/admin/unpublished-list?pageNumber=${currentPage}&pageSize=${pageSize}`)
            .then(r => r.json())
            .then(data => {
                if (data.success) {
                    renderAlbums(data.data.records)
                    renderPagination(data.data.pages, data.data.current)
                    $('#unpublished-count').text(data.data.total)
                } else {
                    alert('加载失败: ' + data.message)
                }
            })
            .catch(e => alert('加载失败: ' + e.message))
            .finally(() => {
                $loading.hide()
                $list.show()
                $pagination.show()
            })
    }

    function renderAlbums(albums) {
        const $list = $('#album-list')
        if (!albums.length) {
            return $list.html('<tr><td colspan="7" style="text-align:center;padding:40px;color:var(--text-secondary)">暂无数据</td></tr>')
        }

        const rows = albums.map(album => {
            const status = album.publishFlg === '0' ? '未发布' : '无匹配'
            const statusCls = album.publishFlg === '0' ? 'status-0' : 'status-2'
            const date = album.publishDate ? new Date(album.publishDate).toLocaleDateString('zh-CN') : '-'

            return `
        <tr>
          <td>${album.id}</td>
          <td><span class="clickable search-trigger" data-artist="${album.artist || ''}" data-date="${date}" data-title="${album.title || ''}" data-id="${album.id}">${album.title || ''}</span></td>
          <td>${album.cloudMusicId || '-'}</td>
          <td><span class="status ${statusCls}">${status}</span></td>
          <td>${date}</td>
          <td>${album.accessCount || 0}</td>
          <td style="text-align:right">
            <div style="display:flex;gap:8px;justify-content:flex-end">
              <button class="btn btn-edit" data-id="${album.id}" data-title="${album.title || ''}" data-artist="${album.artist || ''}" data-publish-date="${date}">修复</button>
              ${album.publishFlg === '0' ? `<button class="btn btn-warning" data-no-match="${album.id}">无匹配</button>` : ''}
            </div>
          </td>
        </tr>`
        }).join('')

        $list.html(rows)
    }

    function renderPagination(total, current) {
        const $pagination = $('#pagination')
        if (total <= 1) return $pagination.hide()

        let html = `<span class="page-item ${current === 1 ? 'disabled' : ''}" data-page="${current - 1}">上一页</span>`
        for (let i = 1; i <= total; i++) {
            if (i === 1 || i === total || (i >= current - 2 && i <= current + 2)) {
                html += `<span class="page-item ${i === current ? 'active' : ''}" data-page="${i}">${i}</span>`
            } else if (i === current - 3 || i === current + 3) {
                html += '<span class="page-item disabled">...</span>'
            }
        }
        html += `<span class="page-item ${current === total ? 'disabled' : ''}" data-page="${current + 1}">下一页</span>`
        $pagination.html(html).show()
    }

    function editAlbum(id, title, artist, publishDate) {
        currentAlbumId = id
        currentAlbumTitle = title
        currentAlbumArtist = artist
        currentAlbumPublishDate = publishDate
        $('#searchAlbumTitle').text(title)
        $('#searchAlbumArtist').text(artist || '未知')
        $('#searchAlbumPublishDate').text(publishDate || '未知')
        $('#searchKeyword').val(title)
        $('#searchModal').addClass('active')
    }

    function closeSearchModal() {
        $('#searchModal').removeClass('active')
    }

    function getHighlightClass(value, currentValue) {
        if (!value || !currentValue) return ''

        // 如果value是数组，检查数组中是否有元素与currentValue匹配
        if (Array.isArray(value)) {
            return value.some(v => v.toString().toLowerCase() === currentValue.toString().toLowerCase()) ? 'highlight-match' : ''
        }

        // 如果value不是数组，直接比较
        return value.toString().toLowerCase() === currentValue.toString().toLowerCase() ? 'highlight-match' : ''
    }

    function formatTimestamp(timestamp) {
        if (!timestamp) return null
        const date = new Date(parseInt(timestamp))
        const year = date.getFullYear()
        const month = String(date.getMonth() + 1)
        const day = String(date.getDate())
        return `${year}/${month}/${day}`
    }

    function updateAlbum(cloudMusicId, title, picUrl) {
        // if (!confirm(`更新专辑信息？\n新歌名: ${title}\n网易云ID: ${cloudMusicId}`)) return

        fetch(`/api/admin/update-album`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: `albumId=${currentAlbumId}&cloudMusicId=${cloudMusicId}&title=${encodeURIComponent(title)}&cloudMusicPicUrl=${encodeURIComponent(picUrl)}`
        })
            .then(r => r.json())
            .then(res => {
                if (res.success) {
                    // alert('更新成功！')
                    closeSearchModal()
                    loadUnpublishedAlbums()
                } else {
                    alert('更新失败: ' + res.message)
                }
            })
            .catch(e => alert('更新失败: ' + e.message))
    }

    function markAsNoMatch(albumId) {
        // if (!confirm('标记为无匹配？')) return
        fetch(`/api/admin/mark-no-match?albumId=${albumId}`, {method: 'POST'})
            .then(r => r.json())
            .then(res => {
                if (res.success) {
                    // alert('操作成功！')
                    loadUnpublishedAlbums()
                } else {
                    alert('操作失败: ' + res.message)
                }
            })
            .catch(e => alert('操作失败: ' + e.message))
    }

    // Event bindings
    $('#album-list').on('click', '.btn-edit', function () {
        editAlbum($(this).data('id'), $(this).data('title'), $(this).data('artist'), $(this).data('publish-date'))
    })

    $('#album-list').on('click', '.btn-warning', function () {
        markAsNoMatch($(this).data('no-match'))
    })

    $('#pagination').on('click', '.page-item:not(.disabled)', function () {
        const page = $(this).data('page')
        if (page && page > 0) {
            currentPage = page
            loadUnpublishedAlbums()
        }
    })

    $('.close-btn').on('click', closeSearchModal)
    $('#searchBtn').on('click', searchCloudMusic)
    $('#searchKeyword').on('keypress', e => {
        if (e.key === 'Enter') searchCloudMusic()
    })

    // 点击专辑名称触发搜索
    $('#album-list').on('click', '.search-trigger', function () {
        const id = $(this).data('id')
        const title = $(this).data('title')
        const artist = $(this).data('artist')
        const date = $(this).data('date')

        // 设置当前专辑信息
        currentAlbumId = id
        currentAlbumTitle = title
        currentAlbumArtist = artist
        currentAlbumPublishDate = date

        $('#searchAlbumTitle').text(title)
        $('#searchAlbumArtist').text(artist || '未知')
        $('#searchAlbumPublishDate').text(date || '未知')
        $('#searchKeyword').val(title)

        // 打开模态框并自动搜索
        $('#searchModal').addClass('active')
        searchCloudMusic()
    })

    // 点击弹出框里的专辑名称或艺术家名称时快速搜索
    $('.search-section').on('click', '#searchAlbumTitle', function () {
        // 点击专辑名称时，用专辑名搜索
        $('#searchKeyword').val(currentAlbumTitle)
        searchCloudMusic()
    })

    $('.search-section').on('click', '#searchAlbumArtist', function () {
        // 点击艺术家名称时，用艺术家名搜索
        if (currentAlbumArtist && currentAlbumArtist !== '未知') {
            $('#searchKeyword').val(currentAlbumArtist)
            searchCloudMusic()
        }
    })

    function searchCloudMusic() {
        const keyword = $('#searchKeyword').val().trim()
        if (!keyword) return alert('请输入搜索关键词')

        const $btn = $('#searchBtn')
        const $container = $('#searchResultsContainer')
        $btn.text('搜索中...').prop('disabled', true)
        $container.html('<div class="no-results">正在搜索...</div>')

        fetch(`/api/admin/search-cloud-music?keyword=${encodeURIComponent(keyword)}`)
            .then(r => r.json())
            .then(res => {
                if (res.success && Array.isArray(res.data) && res.data.length) {
                    const html = res.data.map(item => {
                        const artistMatch = getHighlightClass(item.artists.map(art => art.name), currentAlbumArtist)
                        const formattedPublishDate = formatTimestamp(item.publishTime)
                        const publishDateMatch = getHighlightClass([formattedPublishDate], currentAlbumPublishDate)

                        return `
              <div class="result-item">
                <div style="display:flex;gap:15px;align-items:center">
                  <img src="${item.picUrl}" style="width:60px;height:60px;border-radius:4px;object-fit:cover" onerror="this.style.display='none'">
                  <div style="flex:1">
                    <div style="font-weight:500;margin-bottom:5px">${item.name}</div>
                    <div style="color:var(--text-secondary);font-size:14px">艺术家: <span class="${artistMatch}">${item.artists.map(art => art.name) || '未知'}</span></div>
                    <div style="color:var(--text-secondary);font-size:13px;margin-top:2px">发布日期: <span class="${publishDateMatch}">${formattedPublishDate || '未知'}</span></div>
                    <div style="color:var(--text-secondary);font-size:12px;margin-top:3px">网易云ID: ${item.id}</div>
                  </div>
                  <div style="display:flex;flex-direction:column;gap:8px">
                    <button class="btn btn-edit" data-select="${item.id}" data-name="${item.name}" data-pic="${item.picUrl || ''}">选择</button>
                  </div>
                </div>
              </div>`
                    }).join('')
                    $container.html(html)
                } else {
                    $container.html('<div class="no-results">未找到匹配结果</div>')
                }
            })
            .catch(e => $container.html(`<div class="no-results">搜索失败: ${e.message}</div>`))
            .finally(() => $btn.text('搜索').prop('disabled', false))
    }

    $('#searchResultsContainer').on('click', '[data-select]', function () {
        const id = $(this).data('select')
        const name = $(this).data('name')
        const pic = $(this).data('pic')
        updateAlbum(id, name, pic)
    })

    // Initial load
    loadUnpublishedAlbums()
})
