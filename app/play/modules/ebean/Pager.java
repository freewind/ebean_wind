package play.modules.ebean;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.join;
import static org.apache.commons.lang.StringUtils.substringAfter;
import static org.apache.commons.lang.StringUtils.substringBefore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.ArrayUtils;

import play.mvc.Http.Request;
import play.mvc.Scope.Params;

import com.avaje.ebean.PagingList;
import com.avaje.ebean.Query;

public class Pager<T> implements Iterable<T> {

	private int page;

	private int pageSize;

	private int totalCount;

	private int pageCount;

	private List<T> list;

	public Pager(List<T> list, int page, int pageSize, int totalCount) {
		this.list = list;
		this.page = page;
		this.pageSize = pageSize;
		this.totalCount = totalCount;
		this.pageCount = totalCount / pageSize
				+ (totalCount % pageSize == 0 ? 0 : 1);
	}

	public Pager(Query query, int page, int pageSize) {
		this.page = page;
		this.pageSize = pageSize;
		PagingList pageList = query.findPagingList(pageSize);
		// page of pageList starts at 0
		this.list = pageList.getPage(page - 1).getList();
		this.totalCount = pageList.getTotalRowCount();
		this.pageCount = pageList.getTotalPageCount();
	}

	public Iterator<T> iterator() {
		return list.iterator();
	}

	public int getTotalCount() {
		return totalCount;
	}

	public int getPageSize() {
		return pageSize;
	}

	public int getPageCount() {
		return pageCount;
	}

	public int getPage() {
		return page;
	}

	public List<T> getList() {
		return this.list;
	}

	/**
	 * $linkFirst $linkPrevious ~4~ $linkNext $linkLast
	 * 
	 * @param template
	 * @param params
	 * @return
	 */
	public String createLinks(String template, String... params) {
		String pageValue = Params.current().get("page");
		int page = 1;
		if (!isBlank(pageValue)) {
			page = Integer.parseInt(pageValue);
		}
		if (page > pageCount) {
			page = pageCount;
		}

		for (String param : params) {
			String key = substringBefore(param, "=");
			String value = substringAfter(param, "=");
			if (key.equals("page")) {
				page = Integer.parseInt(value);
			}
		}
		String url = Request.current().url;
		String rendered = template;
		if (template.contains("$pageCount")) {
			rendered = rendered.replace("$pageCount", "" + pageCount);
		}
		if (template.contains("$itemCount")) {
			rendered = rendered.replace("$itemCount", "" + totalCount);
		}
		if (template.contains("$currentPage")) {
			rendered = rendered.replace("$currentPage", "" + page);
		}
		if (template.contains("$linkFirst")) {
			String $linkFirst = "";
			if (page > 1) {
				$linkFirst = "<a class='linkOthers' href='"
						+ updateParam(url, "page", "1") + "'>首页</a>";
			}
			rendered = rendered.replace("$linkFirst", $linkFirst);
		}
		if (template.contains("$linkPrevious")) {
			String $linkPrevious = "";
			if (page > 1) {
				$linkPrevious = "<a class='linkOthers' href='"
						+ updateParam(url, "page", String.valueOf(page - 1))
						+ "'>上一页</a>";
			}
			rendered = rendered.replace("$linkPrevious", $linkPrevious);
		}
		if (template.contains("$linkNext")) {
			String $linkNext = "";
			if (page < pageCount) {
				$linkNext = "<a class='linkOthers' href='"
						+ updateParam(url, "page", String.valueOf(page + 1))
						+ "'>下一页</a>";
			}
			rendered = rendered.replace("$linkNext", $linkNext);
		}
		if (template.contains("$linkLast")) {
			String $linkLast = "";
			if (page < pageCount) {
				$linkLast = "<a class='linkOthers' href='"
						+ updateParam(url, "page", String.valueOf(pageCount))
						+ "'>尾页</a>";
			}
			rendered = rendered.replace("$linkLast", $linkLast);
		}
		if (template.contains("$goto")) {
			StringBuilder $goto = new StringBuilder(
					"跳转至第<input type='text' name='page' size='3' onkeypress=\"if(event.keyCode==13) { window.location=$(this).attr('href')+'&page='+$(this).attr('value');return false;} \" href='"
							+ updateParam(url, "page", null) + "' value='");
			if (page < pageCount) {
				$goto.append((page + 1));
			} else {
				$goto.append("1");
			}
			$goto.append("' />页");
			rendered = rendered.replace("$goto", $goto.toString());
		}
		if (template.contains("$~")) {
			int displayCount = 0;
			Pattern pattern = Pattern.compile(".*[$][~](\\d+)[~].*");
			Matcher matcher = pattern.matcher(template);
			if (matcher.matches()) {
				String displayCountStr = matcher.group(1);
				displayCount = Integer.parseInt(displayCountStr);
				StringBuilder sb = new StringBuilder();
				// specified display count
				displayCount = Math.min(displayCount, pageCount);

				int start = page - displayCount / 2;
				if (displayCount % 2 == 0) {
					start += 1;
				}
				// index from 1 -> from 0
				start -= 1;
				if (start < 0) {
					start = 0;
				} else if (start + displayCount > pageCount) {
					start = pageCount - displayCount;
				}

				if (start > 0) {
					sb.append("<span class='more'>...</span>");
				}
				for (int i = start; i < start + displayCount; i++) {
					sb.append("<a");
					if (i == page - 1) {
						sb.append(" class='current'");
					} else {
						sb.append(" class='pageLink'");
					}
					sb.append(" href='");
					sb.append(updateParam(url, "page", String.valueOf(i + 1)));
					sb.append("'>").append(i + 1).append("</a>");
				}
				if (start + displayCount < pageCount) {
					sb.append("<span class='more'>...</span>");
				}
				rendered = rendered.replace("$~" + displayCountStr + "~",
						sb.toString());
			}
		}

		return "<span class='pager'>" + rendered + "</span>";
	}

	private String updateParam(String url, String paramName, String paramValue) {
		String query = substringAfter(url, "?");
		if (isBlank(query)) {
			if (paramValue != null) {
				return url + "?" + paramName + "=" + paramValue;
			}
		} else {
			String[] items = query.split("&");
			boolean found = false;
			for (int i = 0; i < items.length; i++) {
				String item = items[i];
				String key = substringBefore(item, "=");
				if (key.equals(paramName)) {
					if (paramValue == null) {
						items[i] = null;
					} else {
						items[i] = key + "=" + paramValue;
					}
					found = true;
				}
			}
			url = substringBefore(url, "?") + "?"
					+ join(ArrayUtils.removeElement(items, null), "&");
			if (!found && paramValue != null) {
				url += "&" + paramName + "=" + paramValue;
			}
			return url;
		}
		return url;
	}
}
