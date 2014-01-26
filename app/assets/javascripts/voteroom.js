if (typeof(voteroom) == "undefined") voteroom = {};

$(function() {
	var debug = new Debugger($("#debug"));
	function Debugger($el, max) {
		max = max | 10;
		var cnt = 0;
		function log(msg) {
			if ($el.length) {
				cnt++;
				var $p = $("<p/>");
				$p.text(msg);
				
				if (cnt > max) {
					$el.find("p:first").remove();
				}
				$el.append($p);
			}
		}
		$.extend(this, {
			"log" : log
		});
	}
	function isIOS() {
		var ua = navigator.userAgent.toLowerCase();
		debug.log("UserAgent: " + ua);
		if (ua.indexOf("iphone") != -1) return true;
		if (ua.indexOf("ipad") != -1) return true;
		if (ua.indexOf("ipod") != -1) return true;
		
		return false;
	}
	voteroom.VoteRoom = function(uri) {
		function createWebSocket() {
			debug.log("createWebSocket");
			var ret = new WebSocket(uri);
			ret.onopen = openEvent;
			ret.onmessage = receiveEvent;
			ret.onclose = closeEvent;
			return ret;
		}
		function receiveEvent(event) {
			debug.log("receive: " + event.data);
			var data = JSON.parse(event.data);
			
			// Handle errors
			if(data.error) {
				ws.onclose = null;
				ws.close();
				$("#onError span").text(data.error);
				$("#onError").show();
				return;
			}
			if (data.kind == "member") {
				$member.text(data.count);
			} else if (data.kind == "vote") {
				var $b = $("#num-" + data.key),
					n1 = parseInt(data.count),
					n2 = parseInt($b.text());
				debug.log(n1 + ", " + n2)
				if (n1 > n2) {
					$b.text(n1);
				}
			} else {
				debug.log("Unknown event: " + event.data);
			}
		}
		function openEvent(evt) {
			debug.log("open: " + retryCount);
			retryCount = 0;
			setTimeout(function() {
				ws.send("###member###");
			}, 1000);
		}
		function closeEvent(evt) {
			debug.log("close: " + retryCount)
			if (retryCount > MAX_RETRY_COUNT) {
				$("#onError span").text("Connection interrupted!")
				$("#onError").show()
			} else {
				var next = RETRY_INTERVAL_BASE * retryCount * 1000;
				setTimeout(function() {
					ws = createWebSocket();
				}, next)
			}
			retryCount++;
		}
		function clickEvent(evt) {
			var b = $(this),
				key = b.attr("data-key");
			debug.log("click: " + key);
			
			b.css("background-color", "#ccc");
			setTimeout(function() {
				b.css("background-color", b.attr("data-color"));
			}, 80);
			
			if (!ws || ws.readyState != 1) {
				ws = createWebSocket();
			}
			ws.send(key);
			cnt++;
			$yours.text(cnt);
		}
		window.onunload = function() {
			if (ws) {
				ws.onclose = null;
			}
		}
		if (isIOS()) {
			$(".vote").on("touchstart", clickEvent);
		} else {
			$(".vote").click(clickEvent);
		}
		var MAX_RETRY_COUNT = 5,
		    RETRY_INTERVAL_BASE = 5,
		    cnt = 0,
		    retryCount = 0,
		    $member = $("#member"),
		    $yours = $("#yours"),
		    ws = createWebSocket();
		setInterval(function() {
			if (ws) ws.send("###dummy###");
		}, 25000);
		if (location.hash == "#debug") {
			$("#debug").show();
		}
	}
})
