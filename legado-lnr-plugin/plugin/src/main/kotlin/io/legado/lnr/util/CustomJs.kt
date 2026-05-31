package io.legado.lnr.util

import android.util.Log

/**
 * 段评 custom.js 注入脚本
 * 用于在 WebView 中生成 SVG 段评标记、坐标定位、点击浮层
 */
object CustomJs {

    private const val TAG = "CustomJs"

    /**
     * 获取段评注入脚本
     * @param apiUrl 段评 API 地址
     * @param bookId 书籍 ID
     * @param chapterId 章节 ID
     * @return 注入的 JavaScript 代码
     */
    fun getParagraphReviewScript(
        apiUrl: String,
        bookId: String,
        chapterId: String
    ): String {
        return """
(function() {
    'use strict';

    var API_URL = '${apiUrl}';
    var BOOK_ID = '${bookId}';
    var CHAPTER_ID = '${chapterId}';

    // 段评数据缓存
    var reviewCache = {};
    var reviewEnabled = true;

    // 创建段评标记层
    var overlay = document.createElement('div');
    overlay.id = 'paragraph-review-overlay';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:9999;';
    document.body.appendChild(overlay);

    // 创建浮层容器
    var popup = document.createElement('div');
    popup.id = 'review-popup';
    popup.style.cssText = 'display:none;position:fixed;background:#2a2a3e;border:1px solid #444;border-radius:12px;padding:16px;max-width:80%;max-height:60%;overflow-y:auto;z-index:10000;box-shadow:0 4px 20px rgba(0,0,0,0.5);color:#e0e0e0;font-size:14px;';
    document.body.appendChild(popup);

    // 获取段落元素
    function getParagraphs() {
        var content = document.getElementById('content') || document.querySelector('.content') || document.querySelector('[class*="content"]');
        if (!content) return [];
        return Array.from(content.querySelectorAll('p, div.paragraph, [data-paragraph]'));
    }

    // 为每个段落添加点击区域
    function addReviewMarkers() {
        var paragraphs = getParagraphs();
        paragraphs.forEach(function(p, index) {
            if (p.dataset.reviewMarked) return;
            p.dataset.reviewMarked = 'true';
            p.dataset.paragraphIndex = index;
            p.style.cursor = 'pointer';
            p.style.position = 'relative';

            // 添加段评指示器
            var indicator = document.createElement('span');
            indicator.className = 'review-indicator';
            indicator.dataset.index = index;
            indicator.style.cssText = 'position:absolute;right:-24px;top:50%;transform:translateY(-50%);width:16px;height:16px;border-radius:50%;background:#0f3460;opacity:0.3;cursor:pointer;pointer-events:auto;';
            p.appendChild(indicator);

            // 点击事件
            p.addEventListener('click', function(e) {
                if (!reviewEnabled) return;
                showReviewPopup(index, e.clientY);
            });
        });
    }

    // 显示段评浮层
    function showReviewPopup(paragraphIndex, y) {
        var reviews = reviewCache[paragraphIndex] || [];
        popup.innerHTML = '';

        var title = document.createElement('div');
        title.style.cssText = 'font-weight:bold;margin-bottom:12px;font-size:15px;';
        title.textContent = '段评 (' + reviews.length + ')';
        popup.appendChild(title);

        if (reviews.length === 0) {
            var empty = document.createElement('div');
            empty.style.cssText = 'color:#888;text-align:center;padding:20px;';
            empty.textContent = '暂无段评';
            popup.appendChild(empty);
        } else {
            reviews.forEach(function(review) {
                var item = document.createElement('div');
                item.style.cssText = 'padding:8px 0;border-bottom:1px solid #333;';
                var user = document.createElement('div');
                user.style.cssText = 'color:#4a9eff;font-size:12px;margin-bottom:4px;';
                user.textContent = review.user || '匿名';
                var text = document.createElement('div');
                text.textContent = review.content || '';
                item.appendChild(user);
                item.appendChild(text);
                popup.appendChild(item);
            });
        }

        // 定位浮层
        popup.style.display = 'block';
        popup.style.top = Math.min(y, window.innerHeight - 200) + 'px';
        popup.style.left = '10%';

        // 点击其他区域关闭
        setTimeout(function() {
            document.addEventListener('click', closePopup, { once: true });
        }, 100);
    }

    function closePopup() {
        popup.style.display = 'none';
    }

    // 加载段评数据
    function loadReviews() {
        if (!API_URL) return;
        fetch(API_URL + '/reviews?bookId=' + BOOK_ID + '&chapterId=' + CHAPTER_ID)
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data && data.reviews) {
                    data.reviews.forEach(function(r) {
                        var idx = r.paragraphIndex || 0;
                        if (!reviewCache[idx]) reviewCache[idx] = [];
                        reviewCache[idx].push(r);
                    });
                    updateIndicators();
                }
            })
            .catch(function(e) { console.log('段评加载失败:', e); });
    }

    // 更新段评指示器颜色
    function updateIndicators() {
        var indicators = document.querySelectorAll('.review-indicator');
        indicators.forEach(function(ind) {
            var idx = parseInt(ind.dataset.index);
            if (reviewCache[idx] && reviewCache[idx].length > 0) {
                ind.style.background = '#e94560';
                ind.style.opacity = '0.8';
                ind.title = reviewCache[idx].length + ' 条段评';
            }
        });
    }

    // 开关段评
    window.toggleParagraphReview = function(enabled) {
        reviewEnabled = enabled;
        overlay.style.display = enabled ? 'block' : 'none';
        if (!enabled) closePopup();
    };

    // 初始化
    addReviewMarkers();
    loadReviews();

    // 监听内容变化
    var observer = new MutationObserver(function() {
        addReviewMarkers();
    });
    var content = document.getElementById('content') || document.body;
    observer.observe(content, { childList: true, subtree: true });
})();
        """.trimIndent()
    }
}
