package experimental.sam.bluetooth;

import android.app.Activity;
import android.app.Dialog;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;

class FileChooser {

    private static final String PARENT_DIR="..";

    private Activity activity;
    private ListView listView;
    private Dialog dialog;
    private File currentPath;

    private String extension = null;
    private FileSelectedListener fileListener;

    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
    }

    /**
     * Set a file extension to filter by
     * @param extension String representing file description
     */
    void setExtension(String extension) {
        this.extension = (extension == null) ? null : extension.toLowerCase();
    }

    interface FileSelectedListener {
        void fileSelected(File file);
    }
    FileChooser setFileListener(FileSelectedListener fileListener) {
        this.fileListener = fileListener;
        return this;
    }

    FileChooser(Activity activity) {
        if (isExternalStorageReadable()) {
            this.activity = activity;
            dialog = new Dialog(activity);
            listView = new ListView(activity);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int which, long id) {
                    String fileChosen = (String) listView.getItemAtPosition(which);
                    File chosenFile = getChosenFile(fileChosen);
                    if (chosenFile.isDirectory()) {
                        refresh(chosenFile);
                    } else {
                        if (fileListener != null) {
                            fileListener.fileSelected(chosenFile);
                        }
                        dialog.dismiss();
                    }
                }
            });
            dialog.setContentView(listView);
            dialog.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            refresh(Environment.getExternalStorageDirectory());
        } else {
            Toast.makeText(activity, R.string.storage_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    void showDialog() {
        dialog.show();
    }

    private void refresh(File path) {
        this.currentPath = path;
        if (path.exists()) {
            File[] dirs = path.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return (file.isDirectory() && file.canRead());
                }
            });
            File[] files = path.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    if (!file.isDirectory()) {
                        if (!file.canRead()) {
                            return false;
                        } else if (extension == null) {
                            return true;
                        } else {
                            return file.getName().toLowerCase().endsWith(extension);
                        }
                    }else {
                        return false;
                    }
                }
            });

            int i = 0;
            String[] fileList;
            if (path.getParentFile() == null) {
                fileList = new String[dirs.length + files.length];
            } else {
                fileList = new String[dirs.length + files.length + 1];
                fileList[i++] = PARENT_DIR;
            }
            Arrays.sort(dirs);
            Arrays.sort(files);
            for (File dir : dirs) { fileList[i++] = dir.getName(); }
            for (File file : files) { fileList[i++] = file.getName(); }

            dialog.setTitle(currentPath.getPath());
            listView.setAdapter(new ArrayAdapter<String>(activity,
                    android.R.layout.simple_list_item_1, fileList) {
                @Override
                public @NonNull View getView(int pos, View view, @NonNull ViewGroup parent) {
                    view = super.getView(pos, view, parent);
                    ((TextView) view).setSingleLine(true);
                    return view;
                }
            });
        }
    }

    private File getChosenFile(String fileChosen) {
        if (fileChosen.equals(PARENT_DIR)) {
            return currentPath.getParentFile();
        } else {
            return new File(currentPath, fileChosen);
        }
    }
}
