package play.modules.ebean;

import play.exceptions.PlayException;

public class EbeanException extends PlayException {

	public EbeanException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public String getErrorDescription() {
		return this.getMessage();
	}

	@Override
	public String getErrorTitle() {
		return this.getMessage();
	}

}
