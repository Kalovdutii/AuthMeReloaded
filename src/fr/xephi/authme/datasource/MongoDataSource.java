package fr.xephi.authme.datasource;

import java.util.List;
import java.util.Arrays;
import fr.xephi.authme.cache.auth.PlayerAuth;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
import com.mongodb.ServerAddress;

public class MongoDataSource implements DataSource {

	@Override
	public boolean isAuthAvailable(String user) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public PlayerAuth getAuth(String user) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean saveAuth(PlayerAuth auth) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updateSession(PlayerAuth auth) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updatePassword(PlayerAuth auth) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int purgeDatabase(long until) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<String> autoPurgeDatabase(long until) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean removeAuth(String user) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updateQuitLoc(PlayerAuth auth) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getIps(String ip) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<String> getAllAuthsByName(PlayerAuth auth) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getAllAuthsByIp(String ip) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getAllAuthsByEmail(String email) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean updateEmail(PlayerAuth auth) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updateSalt(PlayerAuth auth) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub

	}

	@Override
	public void reload() {
		// TODO Auto-generated method stub

	}

	@Override
	public void purgeBanned(List<String> banned) {
		// TODO Auto-generated method stub

	}

	@Override
	public DataSourceType getType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isLogged(String user) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setLogged(String user) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setUnlogged(String user) {
		// TODO Auto-generated method stub

	}

	@Override
	public void purgeLogged() {
		// TODO Auto-generated method stub

	}

}
