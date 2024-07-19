package com.htf.drdshsdklibrary.Utills.FileCompression;

import java.io.File;
import java.util.List;


public interface IImageCompressTaskListener {

    public void onComplete(List<File> compressed);
    public void onError(Throwable error);
}
