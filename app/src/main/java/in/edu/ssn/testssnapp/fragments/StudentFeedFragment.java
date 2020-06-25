package in.edu.ssn.testssnapp.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.firebase.ui.common.ChangeEventType;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.firebase.ui.firestore.SnapshotParser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.hendraanggrian.appcompat.widget.SocialTextView;

import in.edu.ssn.testssnapp.ClubPageActivity;
import in.edu.ssn.testssnapp.NoNetworkActivity;
import in.edu.ssn.testssnapp.PdfViewerActivity;
import in.edu.ssn.testssnapp.PostDetailsActivity;
import in.edu.ssn.testssnapp.R;
import in.edu.ssn.testssnapp.adapters.ImageAdapter;
import in.edu.ssn.testssnapp.models.Club;
import in.edu.ssn.testssnapp.models.Post;
import in.edu.ssn.testssnapp.utils.CommonUtils;
import in.edu.ssn.testssnapp.utils.Constants;
import in.edu.ssn.testssnapp.utils.SharedPref;
import spencerstudios.com.bungeelib.Bungee;

public class StudentFeedFragment extends Fragment {

    private static final String TAG = "StudentFeedFragmentTest";
    boolean darkMode = false;
    private RecyclerView feedsRV;
    private LinearLayoutManager layoutManager;
    private RelativeLayout layout_progress;
    private ShimmerFrameLayout shimmer_view;
    private FirestoreRecyclerAdapter adapter;
    private TextView newPostTV, linkTitleTV2;
    private CardView syllabusCV, libraryCV, lakshyaCV, lmsCV;
    private String dept;

    public StudentFeedFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        CommonUtils.addScreen(getContext(), getActivity(), "StudentFeedFragment");
        darkMode = SharedPref.getBoolean(getContext(), "dark_mode");
        View view;
        if (darkMode) {
            view = inflater.inflate(R.layout.fragment_student_feed_dark, container, false);
        } else {
            view = inflater.inflate(R.layout.fragment_student_feed, container, false);
        }

        CommonUtils.initFonts(getContext(), view);
        initUI(view);
        setupFireStore();

        newPostTV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                feedsRV.smoothScrollToPosition(0);
                newPostTV.setVisibility(View.GONE);
            }
        });

        dept = SharedPref.getString(getContext(), "dept");
        libraryCV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (CommonUtils.checkWifiOnAndConnected(getContext(), "ssn")) {
                    CommonUtils.openCustomBrowser(getContext(), "http://opac.ssn.net:8081/");
                } else {
                    Toast toast = Toast.makeText(getContext(), "Please connect to SSN wifi ", Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
            }
        });
        lakshyaCV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!CommonUtils.alerter(getContext())) {
                    FirebaseFirestore.getInstance().collection(Constants.collection_club).whereEqualTo("name", Constants.lakshya).addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                            if (queryDocumentSnapshots != null) {
                                DocumentSnapshot ds = queryDocumentSnapshots.getDocuments().get(0);
                                Club model = CommonUtils.getClubFromSnapshot(getContext(), ds);

                                Intent intent = new Intent(getContext(), ClubPageActivity.class);
                                intent.putExtra("data", model);
                                getContext().startActivity(intent);
                                Bungee.slideLeft(getContext());
                            }
                        }
                    });
                } else {
                    Intent intent = new Intent(getContext(), NoNetworkActivity.class);
                    intent.putExtra("key", "home");
                    startActivity(intent);
                    Bungee.fade(getContext());
                }
            }
        });
        lmsCV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!CommonUtils.alerter(getContext())) {
                    CommonUtils.openCustomBrowser(getContext(), Constants.lms);
                } else {
                    Intent intent = new Intent(getContext(), NoNetworkActivity.class);
                    intent.putExtra("key", "home");
                    startActivity(intent);
                    Bungee.fade(getContext());
                }
            }
        });
        syllabusCV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int year = SharedPref.getInt(getContext(), "year");
                if (year == 2016 || year == 2017) {
                    switch (dept) {
                        case "cse":
                            openSyllabus(Constants.cseAU);
                            break;
                        case "it":
                            openSyllabus(Constants.itAU);
                            break;
                        case "ece":
                            openSyllabus(Constants.eceAU);
                            break;
                        case "eee":
                            openSyllabus(Constants.eeeAU);
                            break;
                        case "che":
                            openSyllabus(Constants.cheAU);
                            break;
                        case "bme":
                            openSyllabus(Constants.bmeAU);
                            break;
                        case "civ":
                            openSyllabus(Constants.civAU);
                            break;
                        case "mec":
                            openSyllabus(Constants.mecAU);
                            break;
                    }
                } else {
                    switch (dept) {
                        case "cse":
                            openSyllabus(Constants.cseAN);
                            break;
                        case "it":
                            openSyllabus(Constants.itAN);
                            break;
                        case "ece":
                            openSyllabus(Constants.eceAN);
                            break;
                        case "eee":
                            openSyllabus(Constants.eeeAN);
                            break;
                        case "che":
                            openSyllabus(Constants.cheAN);
                            break;
                        case "bme":
                            openSyllabus(Constants.bmeAN);
                            break;
                        case "civ":
                            openSyllabus(Constants.civAN);
                            break;
                        case "mec":
                            openSyllabus(Constants.mecAN);
                            break;
                    }
                }
            }
        });

        return view;
    }

    /*********************************************************/

    private void setupFireStore() {
        String dept = SharedPref.getString(getContext(), "dept");
        String year = "year." + SharedPref.getInt(getContext(), "year");

        final TextDrawable.IBuilder builder = TextDrawable.builder()
                .beginConfig()
                .toUpperCase()
                .endConfig()
                .round();

        Query query = FirebaseFirestore.getInstance().collection(Constants.collection_post).whereArrayContains("dept", dept).whereEqualTo(year, true).orderBy("time", Query.Direction.DESCENDING);
        FirestoreRecyclerOptions<Post> options = new FirestoreRecyclerOptions.Builder<Post>().setQuery(query, new SnapshotParser<Post>() {
            @NonNull
            @Override
            public Post parseSnapshot(@NonNull DocumentSnapshot snapshot) {
                shimmer_view.setVisibility(View.VISIBLE);
                return CommonUtils.getPostFromSnapshot(getContext(), snapshot);
            }
        }).build();
        adapter = new FirestoreRecyclerAdapter<Post, FeedViewHolder>(options) {
            @Override
            public void onBindViewHolder(final FeedViewHolder holder, final int position, final Post model) {
                holder.authorTV.setText(model.getAuthor());

                ColorGenerator generator = ColorGenerator.MATERIAL;
                int color = generator.getColor(model.getAuthor_image_url());
                TextDrawable ic1 = builder.build(String.valueOf(model.getAuthor().charAt(0)), color);
                holder.userImageIV.setImageDrawable(ic1);

                holder.positionTV.setText(model.getPosition());
                holder.titleTV.setText(model.getTitle());
                holder.timeTV.setText(CommonUtils.getTime(model.getTime()));

                if (model.getDescription().length() > 100) {
                    SpannableString ss = new SpannableString(model.getDescription().substring(0, 100) + "... see more");
                    ss.setSpan(new RelativeSizeSpan(0.9f), ss.length() - 12, ss.length(), 0);
                    ss.setSpan(new ForegroundColorSpan(Color.parseColor("#404040")), ss.length() - 12, ss.length(), 0);
                    holder.descriptionTV.setText(ss);
                } else
                    holder.descriptionTV.setText(model.getDescription().trim());

                if (model.getImageUrl() != null && model.getImageUrl().size() != 0) {
                    holder.viewPager.setVisibility(View.VISIBLE);

                    final ImageAdapter imageAdapter = new ImageAdapter(getContext(), model.getImageUrl(), 1, model);
                    holder.viewPager.setAdapter(imageAdapter);

                    if (model.getImageUrl().size() == 1) {
                        holder.current_imageTV.setVisibility(View.GONE);
                    } else {
                        holder.current_imageTV.setVisibility(View.VISIBLE);
                        holder.current_imageTV.setText(1 + " / " + model.getImageUrl().size());
                        holder.viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                            @Override
                            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                            }

                            @Override
                            public void onPageSelected(int pos) {
                                holder.current_imageTV.setText((pos + 1) + " / " + model.getImageUrl().size());
                            }

                            @Override
                            public void onPageScrollStateChanged(int state) {

                            }
                        });
                    }
                } else {
                    holder.viewPager.setVisibility(View.GONE);
                    holder.current_imageTV.setVisibility(View.GONE);
                }

                holder.feed_view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getContext(), PostDetailsActivity.class);
                        intent.putExtra("post", model);
                        intent.putExtra("type", 1);
                        startActivity(intent);
                        Bungee.slideLeft(getContext());
                    }
                });
                holder.feed_view.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        CommonUtils.handleBottomSheet(v, model, Constants.post, getContext());
                        return true;
                    }
                });

                layout_progress.setVisibility(View.GONE);
                shimmer_view.setVisibility(View.GONE);
            }

            @NonNull
            @Override
            public FeedViewHolder onCreateViewHolder(@NonNull ViewGroup group, int i) {
                View view;
                if (SharedPref.getBoolean(getContext(), "dark_mode")) {
                    view = LayoutInflater.from(group.getContext()).inflate(R.layout.student_post_item_dark, group, false);
                } else {
                    view = LayoutInflater.from(group.getContext()).inflate(R.layout.student_post_item, group, false);
                }

                return new FeedViewHolder(view);
            }


            @Override
            public void onChildChanged(@NonNull ChangeEventType type, @NonNull DocumentSnapshot snapshot, int newIndex, int oldIndex) {
                super.onChildChanged(type, snapshot, newIndex, oldIndex);
                if (type == ChangeEventType.CHANGED) {
                    // New post added (Show new post available text)
                    newPostTV.setVisibility(View.VISIBLE);
                }
            }
        };
        feedsRV.setAdapter(adapter);
    }

    private void initUI(View view) {
        feedsRV = view.findViewById(R.id.feedsRV);
        newPostTV = view.findViewById(R.id.newPostTV);
        layoutManager = new LinearLayoutManager(getContext());
        feedsRV.setLayoutManager(layoutManager);
        feedsRV.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (layoutManager.findFirstCompletelyVisibleItemPosition() == 0) {
                    newPostTV.setVisibility(View.GONE);
                }
            }
        });
        shimmer_view = view.findViewById(R.id.shimmer_view);
        layout_progress = view.findViewById(R.id.layout_progress);
        syllabusCV = view.findViewById(R.id.syllabusCV);
        libraryCV = view.findViewById(R.id.libraryCV);
        lmsCV = view.findViewById(R.id.lmsCV);
        lakshyaCV = view.findViewById(R.id.lakshyaCV);

        linkTitleTV2 = view.findViewById(R.id.linkTitleTV2);
        linkTitleTV2.setSelected(true);
    }

    /*********************************************************/

    public void openSyllabus(String url) {
        if (!CommonUtils.alerter(getContext())) {
            Intent i = new Intent(getContext(), PdfViewerActivity.class);
            i.putExtra(Constants.PDF_URL, url);
            startActivity(i);
            Bungee.fade(getContext());
        } else {
            Intent intent = new Intent(getContext(), NoNetworkActivity.class);
            intent.putExtra("key", "home");
            startActivity(intent);
            Bungee.fade(getContext());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        adapter.startListening();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (adapter != null)
            adapter.stopListening();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    /*********************************************************/

    public class FeedViewHolder extends RecyclerView.ViewHolder {
        public TextView authorTV, positionTV, titleTV, timeTV, current_imageTV;
        public SocialTextView descriptionTV;
        public ImageView userImageIV;
        public RelativeLayout feed_view;
        public ViewPager viewPager;

        public FeedViewHolder(View itemView) {
            super(itemView);

            authorTV = itemView.findViewById(R.id.authorTV);
            positionTV = itemView.findViewById(R.id.positionTV);
            titleTV = itemView.findViewById(R.id.titleTV);
            descriptionTV = itemView.findViewById(R.id.descriptionTV);
            timeTV = itemView.findViewById(R.id.timeTV);
            current_imageTV = itemView.findViewById(R.id.currentImageTV);
            userImageIV = itemView.findViewById(R.id.userImageIV);
            feed_view = itemView.findViewById(R.id.feed_view);
            viewPager = itemView.findViewById(R.id.viewPager);
        }
    }
}