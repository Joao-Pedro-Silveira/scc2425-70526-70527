package tukano.api;

public class ShortDAO extends Short {
    private String _rid;

    public String get_rid() {
        return _rid;
    }

    public void set_rid(String _rid) {
        this._rid = _rid;
    }

    private String _ts;

    public String get_ts() {
        return _ts;
    }

    public void set_ts(String _ts) {
        this._ts = _ts;
    }

    public ShortDAO() {}

    public String toString() {
        return "ShortDAO [_rid=" + _rid + ", _ts=" + _ts + ", short=" + super.toString() + "]";
    }
}
