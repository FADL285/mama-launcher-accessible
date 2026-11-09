package info.fadl.mamalauncher;
/** Fictional read-only provider, never touches a handset. */
abstract class FixtureContactsProvider extends android.content.ContentProvider {
 @Override public boolean onCreate(){return true;}
 @Override public String getType(android.net.Uri uri){return null;}
 @Override public android.net.Uri insert(android.net.Uri uri,android.content.ContentValues values){throw new UnsupportedOperationException();}
 @Override public int update(android.net.Uri uri,android.content.ContentValues values,String s,String[] args){throw new UnsupportedOperationException();}
 @Override public int delete(android.net.Uri uri,String s,String[] args){throw new UnsupportedOperationException();}
}
